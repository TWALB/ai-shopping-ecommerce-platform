package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交易趋势数据点
 */
@Data
public class TrendPointVO {

    private LocalDate date;

    private Long orderCount;

    private BigDecimal amount;
}
