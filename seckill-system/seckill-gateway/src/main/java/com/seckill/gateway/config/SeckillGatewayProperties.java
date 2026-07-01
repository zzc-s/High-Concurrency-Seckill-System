package com.seckill.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seckill.gateway")
public class SeckillGatewayProperties {

    private int tokenBucketRate = 50;
    private int ipLimitPerSecond = 10;
    private int userLimitPerSecond = 3;
    private int blacklistTtlSeconds = 60;
    private int fingerprintTtlSeconds = 300;
}
