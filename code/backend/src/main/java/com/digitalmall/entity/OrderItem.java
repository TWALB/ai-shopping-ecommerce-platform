package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细表（下单时冗余快照商品信息）
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    private Long productId;

    private Long skuId;

    /** 商品名称（快照） */
    private String productName;

    /** 规格名（快照） */
    private String skuName;

    /** 商品图（快照） */
    private String productImage;

    /** 成交单价（快照） */
    private BigDecimal price;

    private Integer quantity;

    /** 小计金额 */
    private BigDecimal totalAmount;
}
