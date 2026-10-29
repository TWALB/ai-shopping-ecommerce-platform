package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单列表项（用户端 / 商家端通用）
 */
@Data
public class AfterSaleItemVO {

    private Long id;

    private String afterSaleNo;

    private Long orderId;

    private String orderNo;

    private Long orderItemId;

    private String productName;

    private String productImage;

    private String skuName;

    private Integer quantity;

    /** 类型：0仅退款 1退货退款 2换货 */
    private Integer type;

    private String reason;

    private BigDecimal amount;

    /** 状态：0待商家处理 1商家同意 2商家拒绝 3平台介入 4已完成 5已撤销 */
    private Integer status;

    private String rejectReason;

    private LocalDateTime createdTime;

    private LocalDateTime handleTime;
}
