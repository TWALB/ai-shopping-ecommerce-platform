package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表项
 */
@Data
public class ProductVO {

    private Long id;

    private String productName;

    private String mainImage;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer sales;

    private String shopName;

    private String tags;

    /** 是否推荐 */
    private Integer isRecommend;
}
