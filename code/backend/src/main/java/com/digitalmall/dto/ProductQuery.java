package com.digitalmall.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品分页查询条件
 */
@Data
public class ProductQuery {

    private String keyword;

    private Long categoryId;

    private Long brandId;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /** comprehensive / sales / price / view_count / recommend */
    private String sortBy = "comprehensive";

    private String order = "desc";

    private long pageNum = 1;

    private long pageSize = 10;
}
