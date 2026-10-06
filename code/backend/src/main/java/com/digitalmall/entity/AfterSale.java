package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后申请表（退款/退货/换货）
 */
@Data
@TableName("after_sale")
public class AfterSale {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String afterSaleNo;

    private Long orderId;

    private Long orderItemId;

    private Long userId;

    /** 类型：0仅退款 1退货退款 2换货 */
    private Integer type;

    private String reason;

    /** 申请退款金额 */
    private BigDecimal amount;

    /** 状态：0待商家处理 1商家同意 2商家拒绝 3平台介入 4已完成 5已撤销 */
    private Integer status;

    private String rejectReason;

    private LocalDateTime handleTime;

    private Long handleBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
