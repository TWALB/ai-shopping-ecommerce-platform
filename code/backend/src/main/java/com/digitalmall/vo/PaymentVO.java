package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付结果/支付查询
 */
@Data
public class PaymentVO {

    private String paymentNo;

    /** 0待支付 1已支付 */
    private Integer status;

    private LocalDateTime payTime;
}
