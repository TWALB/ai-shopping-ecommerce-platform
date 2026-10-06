package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商家经营数据统计
 */
@Data
public class MerchantStatsVO {

    /** 累计销量（本店商品 sales 之和） */
    private Long totalSales;

    /** 累计销售额（本店订单实付合计，不含取消） */
    private BigDecimal totalAmount;

    /** 今日销售额（今日支付订单实付合计） */
    private BigDecimal todayAmount;

    /** 待发货订单数 */
    private Long pendingShipCount;

    /** 热销商品 Top5 */
    private List<TopProductVO> topProducts;

    @Data
    public static class TopProductVO {
        private Long productId;
        private String productName;
        private Integer sales;
        private BigDecimal amount;
    }
}
