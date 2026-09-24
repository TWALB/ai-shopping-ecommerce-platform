package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单结果（按店铺分单，一次可能返回多单）
 */
@Data
public class CreateOrderVO {

    private Long orderId;

    private String orderNo;

    private BigDecimal payAmount;

    /** 0待付款 */
    private Integer status;
}
