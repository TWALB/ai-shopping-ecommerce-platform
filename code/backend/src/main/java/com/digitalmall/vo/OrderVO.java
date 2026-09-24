package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项
 */
@Data
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long shopId;

    private String shopName;

    /** 0待付款 1待发货 2待收货 3已完成 4已取消 5售后中 */
    private Integer status;

    private BigDecimal totalAmount;

    private BigDecimal freightAmount;

    private BigDecimal payAmount;

    /** 商品件数 */
    private Integer itemCount;

    /** 首图 */
    private String firstImage;

    private LocalDateTime createdTime;
}
