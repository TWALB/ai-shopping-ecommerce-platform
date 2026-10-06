package com.digitalmall.mapper;

import com.digitalmall.vo.MerchantStatsVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家经营统计 Mapper（聚合查询）
 */
public interface MerchantStatsMapper {

    /** 累计销量（本店商品 sales 之和） */
    @Select("SELECT IFNULL(SUM(p.sales), 0) FROM product p WHERE p.shop_id = #{shopId}")
    Long sumSales(@Param("shopId") Long shopId);

    /** 累计销售额（已支付订单实付合计） */
    @Select("SELECT IFNULL(SUM(o.pay_amount), 0) FROM `order` o WHERE o.shop_id = #{shopId} AND o.pay_time IS NOT NULL")
    BigDecimal sumAmount(@Param("shopId") Long shopId);

    /** 今日销售额 */
    @Select("SELECT IFNULL(SUM(o.pay_amount), 0) FROM `order` o " +
            "WHERE o.shop_id = #{shopId} AND o.pay_time IS NOT NULL AND o.pay_time >= #{today}")
    BigDecimal sumTodayAmount(@Param("shopId") Long shopId, @Param("today") LocalDateTime today);

    /** 待发货订单数 */
    @Select("SELECT COUNT(*) FROM `order` o WHERE o.shop_id = #{shopId} AND o.status = 1")
    Long countPendingShip(@Param("shopId") Long shopId);

    /** 热销商品 Top5（按销量） */
    @Select("SELECT p.id AS product_id, p.product_name, p.sales, p.price * p.sales AS amount " +
            "FROM product p WHERE p.shop_id = #{shopId} ORDER BY p.sales DESC LIMIT 5")
    List<MerchantStatsVO.TopProductVO> topProducts(@Param("shopId") Long shopId);
}
