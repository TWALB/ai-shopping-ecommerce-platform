package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品简要信息（聊天推荐卡片）
 */
@Data
public class ProductBriefVO {

    private Long id;

    private String productName;

    private BigDecimal price;

    private String mainImage;

    private Integer sales;

    /** 检索相似度 0-1 */
    private Double similarity;
}
