package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.config.RabbitMQConfig;
import com.seckill.dto.SeckillResultVO;
import com.seckill.entity.Product;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.StockLog;
import com.seckill.lock.DistributedLockService;
import com.seckill.mapper.ProductMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.mapper.StockLogMapper;
import com.seckill.mq.OrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> stockDeductScript;
    private final RabbitTemplate rabbitTemplate;
    private final SeckillOrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final StockLogMapper stockLogMapper;
    private final DistributedLockService lockService;
    private final RedissonClient redissonClient;

    @Value("${seckill.stock.key-prefix}")
    private String stockKeyPrefix;

    @Value("${seckill.stock.bought-prefix}")
    private String boughtKeyPrefix;

    @Value("${seckill.mq.consumer-rate-limit}")
    private int consumerRateLimit;

    public SeckillResultVO seckill(Long userId, Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() != 1) {
            return new SeckillResultVO("FAILED", "秒杀活动未开始或已结束", null);
        }

        String stockKey = stockKeyPrefix + productId;
        String boughtKey = boughtKeyPrefix + productId;

        Long result = redisTemplate.execute(stockDeductScript,
                List.of(stockKey, boughtKey),
                String.valueOf(userId));

        if (result == null || result == -1L) {
            return new SeckillResultVO("FAILED", "库存不足", null);
        }
        if (result == -2L) {
            return getResult(userId, productId);
        }

        String orderNo = "SK" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);

        SeckillOrder pending = new SeckillOrder();
        pending.setUserId(userId);
        pending.setProductId(productId);
        pending.setOrderNo(orderNo);
        pending.setStatus(0);
        orderMapper.insert(pending);

        OrderMessage message = new OrderMessage(userId, productId, orderNo);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, message);

        return new SeckillResultVO("PENDING", "排队中，请稍后查询结果", orderNo);
    }

    public SeckillResultVO getResult(Long userId, Long productId) {
        SeckillOrder successOrder = orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getUserId, userId)
                .eq(SeckillOrder::getProductId, productId)
                .eq(SeckillOrder::getStatus, 1)
                .orderByDesc(SeckillOrder::getCreateTime)
                .last("LIMIT 1"));
        if (successOrder != null) {
            return new SeckillResultVO("SUCCESS", "秒杀成功", successOrder.getOrderNo());
        }

        SeckillOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getUserId, userId)
                .eq(SeckillOrder::getProductId, productId)
                .orderByDesc(SeckillOrder::getCreateTime)
                .last("LIMIT 1"));
        if (order == null) {
            return new SeckillResultVO("NONE", "未参与秒杀", null);
        }
        if (order.getStatus() == 0 && order.getCreateTime() != null
                && order.getCreateTime().isBefore(LocalDateTime.now().minusSeconds(60))) {
            return new SeckillResultVO("PENDING", "处理较慢，请到「我的订单」查看", order.getOrderNo());
        }
        return switch (order.getStatus()) {
            case 0 -> new SeckillResultVO("PENDING", "处理中", order.getOrderNo());
            case 1 -> new SeckillResultVO("SUCCESS", "秒杀成功", order.getOrderNo());
            case 2 -> new SeckillResultVO("FAILED", "秒杀失败", order.getOrderNo());
            default -> new SeckillResultVO("UNKNOWN", "未知状态", order.getOrderNo());
        };
    }

    public void failOrder(String orderNo) {
        SeckillOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo));
        if (order != null && order.getStatus() == 0) {
            order.setStatus(2);
            orderMapper.updateById(order);
            rollbackRedis(order.getProductId(), order.getUserId());
            log.warn("订单标记失败: orderNo={}", orderNo);
        }
    }

    public void processOrder(OrderMessage message) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter("seckill:order:rate");
        rateLimiter.trySetRate(RateType.OVERALL, consumerRateLimit, 1, RateIntervalUnit.SECONDS);
        rateLimiter.acquire();

        String lockKey = "lock:stock:" + message.getProductId();
        lockService.executeWithLock(lockKey, () -> {
            SeckillOrder order = orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                    .eq(SeckillOrder::getOrderNo, message.getOrderNo()));
            if (order == null || order.getStatus() != 0) {
                return;
            }

            int rows = productMapper.deductStock(message.getProductId());
            if (rows <= 0) {
                order.setStatus(2);
                orderMapper.updateById(order);
                rollbackRedis(message.getProductId(), message.getUserId());
                return;
            }

            order.setStatus(1);
            orderMapper.updateById(order);

            StockLog stockLog = new StockLog();
            stockLog.setProductId(message.getProductId());
            stockLog.setUserId(message.getUserId());
            stockLog.setAction("DEDUCT");
            stockLogMapper.insert(stockLog);
            log.info("订单落库成功: orderNo={}", message.getOrderNo());
        });
    }

    private void rollbackRedis(Long productId, Long userId) {
        redisTemplate.opsForValue().increment(stockKeyPrefix + productId);
        redisTemplate.opsForSet().remove(boughtKeyPrefix + productId, String.valueOf(userId));
    }
}
