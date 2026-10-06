package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 平台商品管理列表项
 */
@Data
public class AdminProductVO {

    private Long id;

    private String productName;

    private String mainImage;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    /** 0草稿 1上架 2下架 */
    private Integer status;

    private String shopName;

    private LocalDateTime createdTime;
}
