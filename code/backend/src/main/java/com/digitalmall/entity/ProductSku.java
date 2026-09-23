package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品SKU表（规格维度的价格与库存）
 */
@Data
@TableName("product_sku")
public class ProductSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** 规格名：曜石黑/12GB+512GB */
    private String skuName;

    private String skuImage;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    /** 0停用 1启用 */
    private Integer status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
