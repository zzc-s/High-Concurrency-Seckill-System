package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.dto.*;
import com.seckill.entity.SeckillOrder;
import com.seckill.service.AuthService;
import com.seckill.service.OrderService;
import com.seckill.service.ProductService;
import com.seckill.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final AuthService authService;
    private final ProductService productService;
    private final SeckillService seckillService;
    private final OrderService orderService;

    @PostMapping("/auth/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/auth/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @GetMapping("/products")
    public Result<List<ProductVO>> products() {
        return Result.ok(productService.listProducts());
    }

    @GetMapping("/products/{id}")
    public Result<ProductVO> product(@PathVariable("id") Long id) {
        return Result.ok(productService.getProduct(id));
    }

    @PostMapping("/seckill/{productId}")
    public Result<SeckillResultVO> seckill(@PathVariable("productId") Long productId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.ok(seckillService.seckill(userId, productId));
    }

    @GetMapping("/seckill/result/{productId}")
    public Result<SeckillResultVO> seckillResult(@PathVariable("productId") Long productId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.ok(seckillService.getResult(userId, productId));
    }

    @GetMapping("/orders")
    public Result<List<SeckillOrder>> orders(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.ok(orderService.listByUser(userId));
    }
}
