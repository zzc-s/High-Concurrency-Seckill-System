package com.seckill.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductVO {

    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private Integer seckillStock;
    private Integer remainStock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
}
