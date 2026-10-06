package com.digitalmall.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 平台统计聚合 Mapper
 */
public interface AdminStatsMapper {

    /** 用户总数 / 商家数 / 商品数 / 订单数 */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM sys_user) AS user_count, " +
            "(SELECT COUNT(*) FROM sys_user WHERE role = 1) AS merchant_count, " +
            "(SELECT COUNT(*) FROM product) AS product_count, " +
            "(SELECT COUNT(*) FROM `order`) AS order_count")
    Map<String, Object> overviewCounts();

    /** 平台累计交易额（已支付订单） */
    @Select("SELECT IFNULL(SUM(pay_amount), 0) FROM `order` WHERE pay_time IS NOT NULL")
    BigDecimal totalAmount();

    /** 今日订单数与交易额 */
    @Select("SELECT COUNT(*) AS order_count, IFNULL(SUM(pay_amount), 0) AS amount " +
            "FROM `order` WHERE pay_time IS NOT NULL AND pay_time >= #{today}")
    Map<String, Object> todayStats(@Param("today") LocalDate today);

    /** 近 N 日交易趋势（按支付日期聚合，日期返回字符串 yyyy-MM-dd） */
    @Select("SELECT DATE_FORMAT(pay_time, '%Y-%m-%d') AS date, COUNT(*) AS order_count, IFNULL(SUM(pay_amount), 0) AS amount " +
            "FROM `order` WHERE pay_time IS NOT NULL AND pay_time >= #{start} " +
            "GROUP BY DATE_FORMAT(pay_time, '%Y-%m-%d') ORDER BY date")
    List<Map<String, Object>> trend(@Param("start") LocalDate start);
}
