package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录表（模拟支付）
 */
@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private Long orderId;

    private Long userId;

    private BigDecimal amount;

    /** 支付方式：0模拟支付 */
    private Integer payType;

    /** 状态：0待支付 1已支付 2已退款 */
    private Integer status;

    private LocalDateTime payTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
