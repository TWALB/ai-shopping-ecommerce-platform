package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 平台数据总览
 */
@Data
public class StatsOverviewVO {

    private Long userCount;

    private Long merchantCount;

    private Long productCount;

    private Long orderCount;

    private BigDecimal totalAmount;

    private Long todayOrderCount;

    private BigDecimal todayAmount;
}
