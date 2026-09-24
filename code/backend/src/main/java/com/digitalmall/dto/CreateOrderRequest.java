package com.digitalmall.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建订单请求体
 */
@Data
public class CreateOrderRequest {

    /** 收货地址ID */
    private Long addressId;

    /** true=结算购物车勾选项；false=直接购买 */
    private Boolean fromCart;

    /** fromCart=false 时必填 */
    private List<OrderItemLine> itemList;

    private String remark;

    @Data
    public static class OrderItemLine {
        private Long skuId;
        private Integer quantity;
    }
}
