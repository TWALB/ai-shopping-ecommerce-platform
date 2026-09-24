package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表（order 为保留字，需反引号）
 */
@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 下单用户ID */
    private Long userId;

    /** 店铺ID */
    private Long shopId;

    /** 商品总金额 */
    private BigDecimal totalAmount;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 收货人姓名（下单快照） */
    private String receiverName;

    /** 收货人电话（快照） */
    private String receiverPhone;

    /** 收货地址（快照） */
    private String receiverAddress;

    /** 状态：0待付款 1待发货 2待收货 3已完成 4已取消 5售后中 */
    private Integer status;

    /** 支付方式：0模拟支付 */
    private Integer payType;

    private LocalDateTime payTime;

    private LocalDateTime deliveryTime;

    private LocalDateTime finishTime;

    private String remark;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
