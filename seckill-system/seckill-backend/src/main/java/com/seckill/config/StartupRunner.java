package com.seckill.config;

import com.seckill.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {

    private final ProductService productService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            productService.warmupAll();
            log.info("Redis 库存预热完成");
        } catch (Exception e) {
            log.warn("库存预热失败（可能数据库未就绪）: {}", e.getMessage());
        }
    }
}
