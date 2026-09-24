package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车表
 */
@Data
@TableName("cart")
public class Cart {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（sys_user.id） */
    private Long userId;

    /** SKU ID（product_sku.id） */
    private Long skuId;

    private Integer quantity;

    /** 是否勾选结算：0否 1是 */
    private Integer isSelected;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
