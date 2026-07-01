package com.seckill.mq;

import com.rabbitmq.client.Channel;
import com.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private final SeckillService seckillService;

    @RabbitListener(queues = "seckill.order.queue")
    public void consume(OrderMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            seckillService.processOrder(message);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("订单处理失败: {}", message.getOrderNo(), e);
            try {
                seckillService.failOrder(message.getOrderNo());
            } catch (Exception ex) {
                log.error("标记订单失败异常: {}", message.getOrderNo(), ex);
            }
            channel.basicNack(tag, false, false);
        }
    }
}
