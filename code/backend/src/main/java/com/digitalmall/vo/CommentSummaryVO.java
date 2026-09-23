package com.digitalmall.vo;

import lombok.Data;

/**
 * 商品评价摘要（详情页展示）
 */
@Data
public class CommentSummaryVO {

    private Long total;

    /** 商品平均分 */
    private Double avgProductScore;

    /** 物流平均分 */
    private Double avgLogisticsScore;
}
