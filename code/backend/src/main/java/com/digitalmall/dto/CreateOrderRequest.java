package com.digitalmall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求体
 */
@Data
public class CreateOrderRequest {

    /** 收货地址ID */
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    /** true=结算购物车勾选项；false=直接购买 */
    private Boolean fromCart;

    /**
     * fromCart=false 时必填。
     * 字段上的 @Valid 不可省略——缺少它时，List 内元素的约束不会生效（嵌套校验不级联）。
     * 此处不加 @NotEmpty：fromCart=true 时该字段本应为 null，否则会误伤正常结算。
     */
    @Valid
    private List<OrderItemLine> itemList;

    private String remark;

    @Data
    public static class OrderItemLine {

        @NotNull(message = "请选择商品规格")
        private Long skuId;

        @NotNull(message = "请填写购买数量")
        @Min(value = 1, message = "购买数量至少为 1")
        @Max(value = 999, message = "单次购买数量不能超过 999")
        private Integer quantity;
    }
}
