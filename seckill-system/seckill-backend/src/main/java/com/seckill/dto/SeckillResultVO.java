package com.seckill.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeckillResultVO {

    private String status;
    private String message;
    private String orderNo;
}
