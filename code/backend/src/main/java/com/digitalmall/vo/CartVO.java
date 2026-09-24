package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车项（含商品与SKU信息）
 */
@Data
public class CartVO {

    private Long id;

    private Long skuId;

    private Long productId;

    private String productName;

    private String mainImage;

    private String skuName;

    private BigDecimal price;

    private Integer quantity;

    /** 是否勾选 */
    private Boolean isSelected;

    private Integer stock;

    /** 商品状态（1上架） */
    private Integer status;

    /** 所属店铺（用于结算分单） */
    private Long shopId;
}
