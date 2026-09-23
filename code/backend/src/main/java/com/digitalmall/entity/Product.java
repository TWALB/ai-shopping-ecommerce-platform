package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品表
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long categoryId;

    private Long brandId;

    private String productName;

    private String productTitle;

    private String mainImage;

    /** 检索关键词（逗号分隔） */
    private String keywords;

    private String tags;

    private String brief;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private Integer sales;

    private Integer viewCount;

    /** 0草稿 1上架 2下架 */
    private Integer status;

    private Integer isRecommend;

    private LocalDateTime shelfTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
