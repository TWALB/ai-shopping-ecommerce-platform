package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价展示项
 */
@Data
public class CommentVO {

    private Long id;

    /** 商品评分 1-5 */
    private Integer productScore;

    /** 物流评分 1-5 */
    private Integer logisticsScore;

    private String content;

    private String imageUrls;

    /** 是否匿名 0否 1是 */
    private Integer isAnonymous;

    /** 评价人昵称（匿名时显示"匿名用户"） */
    private String nickname;

    private String replyContent;

    private LocalDateTime replyTime;

    private LocalDateTime createdTime;

    /** 商品名（管理列表用） */
    private String productName;

    /** 店铺名（管理列表用） */
    private String shopName;

    /** 状态（管理列表用）0待审核 1已展示 2违规删除 */
    private Integer status;
}
