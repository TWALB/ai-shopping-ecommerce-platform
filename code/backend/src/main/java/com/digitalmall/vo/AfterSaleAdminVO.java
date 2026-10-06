package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单管理列表项
 */
@Data
public class AfterSaleAdminVO {

    private Long id;

    private String afterSaleNo;

    private Long orderId;

    /** 类型：0仅退款 1退货退款 2换货 */
    private Integer type;

    private String reason;

    private BigDecimal amount;

    /** 状态：0待商家处理 1商家同意 2商家拒绝 3平台介入 4已完成 5已撤销 */
    private Integer status;

    private String rejectReason;

    private String username;

    private String productName;

    private LocalDateTime handleTime;

    private LocalDateTime createdTime;
}
