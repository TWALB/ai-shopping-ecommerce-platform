package com.digitalmall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 售后申请请求
 */
@Data
public class AfterSaleApplyRequest {

    @NotNull(message = "请选择订单")
    private Long orderId;

    @NotNull(message = "请选择申请售后的商品")
    private Long orderItemId;

    /** 0仅退款 1退货退款 2换货 */
    @NotNull(message = "请选择售后类型")
    private Integer type;

    @NotBlank(message = "请填写申请原因")
    private String reason;

    /** 申请退款金额（换货可为 0） */
    private BigDecimal amount;
}
