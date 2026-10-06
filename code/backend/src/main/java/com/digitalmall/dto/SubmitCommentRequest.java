package com.digitalmall.dto;

import lombok.Data;

import java.util.List;

/**
 * 发表评价请求
 */
@Data
public class SubmitCommentRequest {

    /** 订单ID */
    private Long orderId;

    /** 订单明细ID（按明细评价） */
    private Long orderItemId;

    /** 商品评分 1-5 */
    private Integer productScore;

    /** 物流评分 1-5 */
    private Integer logisticsScore;

    private String content;

    /** 评价图片 URL 列表 */
    private List<String> imageUrls;

    /** 是否匿名 */
    private Boolean isAnonymous;
}
