package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.dto.ProductVO;
import com.seckill.entity.Product;
import com.seckill.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${seckill.stock.key-prefix}")
    private String stockKeyPrefix;

    public List<ProductVO> listProducts() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public ProductVO getProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        return toVO(product);
    }

    public void warmupStock(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        redisTemplate.opsForValue().set(stockKeyPrefix + productId,
                String.valueOf(product.getSeckillStock()));
    }

    public void warmupAll() {
        productMapper.selectList(new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1))
                .forEach(p -> warmupStock(p.getId()));
    }

    public int getRemainStock(Long productId) {
        String val = redisTemplate.opsForValue().get(stockKeyPrefix + productId);
        if (val == null) {
            Product product = productMapper.selectById(productId);
            return product != null ? product.getSeckillStock() : 0;
        }
        return Integer.parseInt(val);
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        vo.setRemainStock(getRemainStock(product.getId()));
        return vo;
    }
}
