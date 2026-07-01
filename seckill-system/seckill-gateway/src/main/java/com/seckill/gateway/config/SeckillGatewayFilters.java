package com.seckill.gateway.config;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SeckillGatewayFilters implements GlobalFilter, Ordered {

    private final RedissonClient redissonClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final SeckillGatewayProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        if (path.contains("/seckill/result/")) {
            return verifyAuthOnly(exchange, chain);
        }

        if (!path.contains("/seckill/") || !"POST".equals(method)) {
            return chain.filter(exchange);
        }

        RRateLimiter limiter = redissonClient.getRateLimiter("gateway:seckill:token");
        limiter.trySetRate(RateType.OVERALL, properties.getTokenBucketRate(), 1, RateIntervalUnit.SECONDS);
        if (!limiter.tryAcquire()) {
            return reject(exchange, "请求过于频繁，请稍后再试");
        }

        String ip = getClientIp(exchange);
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");

        return checkBlacklist(ip)
                .flatMap(blocked -> blocked ? reject(exchange, "IP已被临时封禁") : checkIpLimit(ip))
                .flatMap(ipBlocked -> {
                    if (Boolean.TRUE.equals(ipBlocked)) {
                        return addBlacklist(ip).then(reject(exchange, "IP请求过于频繁"));
                    }
                    if (auth == null || !auth.startsWith("Bearer ")) {
                        return unauthorized(exchange, "未授权请求");
                    }
                    String userKey = DigestUtils.md5DigestAsHex(auth.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
                    return checkUserLimit(userKey)
                            .flatMap(userBlocked -> {
                                if (Boolean.TRUE.equals(userBlocked)) {
                                    return reject(exchange, "用户请求过于频繁");
                                }
                                String fingerprint = DigestUtils.md5DigestAsHex(
                                        (ip + auth + path).getBytes(StandardCharsets.UTF_8));
                                return checkFingerprint(fingerprint)
                                        .flatMap(dup -> dup ? reject(exchange, "重复请求") : chain.filter(exchange));
                            });
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private Mono<Void> verifyAuthOnly(ServerWebExchange exchange, GatewayFilterChain chain) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "未授权请求");
        }
        return chain.filter(exchange);
    }

    private Mono<Boolean> checkBlacklist(String ip) {
        return redisTemplate.hasKey("brush:blacklist:" + ip);
    }

    private Mono<Boolean> addBlacklist(String ip) {
        return redisTemplate.opsForValue()
                .set("brush:blacklist:" + ip, "1", Duration.ofSeconds(properties.getBlacklistTtlSeconds()))
                .thenReturn(true);
    }

    private Mono<Boolean> checkIpLimit(String ip) {
        String key = "brush:ip:" + ip;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> count == 1
                        ? redisTemplate.expire(key, Duration.ofSeconds(1)).thenReturn(count)
                        : Mono.just(count))
                .map(count -> count > properties.getIpLimitPerSecond());
    }

    private Mono<Boolean> checkUserLimit(String userId) {
        String key = "brush:user:" + userId;
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> count == 1
                        ? redisTemplate.expire(key, Duration.ofSeconds(1)).thenReturn(count)
                        : Mono.just(count))
                .map(count -> count > properties.getUserLimitPerSecond());
    }

    private Mono<Boolean> checkFingerprint(String fingerprint) {
        String key = "brush:token:" + fingerprint;
        return redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(properties.getFingerprintTtlSeconds()))
                .map(saved -> !Boolean.TRUE.equals(saved));
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        return jsonResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, 429, message);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return jsonResponse(exchange, HttpStatus.UNAUTHORIZED, 401, message);
    }

    private Mono<Void> jsonResponse(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        byte[] body = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
