package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;

    @PostMapping("/warmup")
    public Result<String> warmupAll() {
        productService.warmupAll();
        return Result.ok("库存预热完成");
    }

    @PostMapping("/warmup/{productId}")
    public Result<String> warmup(@PathVariable("productId") Long productId) {
        productService.warmupStock(productId);
        return Result.ok("商品 " + productId + " 库存预热完成");
    }
}
