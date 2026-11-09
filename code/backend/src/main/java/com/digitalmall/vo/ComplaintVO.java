package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 咨询/投诉列表项（用户端 / 商家端通用）
 */
@Data
public class ComplaintVO {

    private Long id;

    private Long userId;

    private Long shopId;

    private Long orderId;

    private String orderNo;

    /** 发起人（昵称优先，空则用户名） */
    private String username;

    private String shopName;

    /** 类型：0咨询 1投诉 */
    private Integer type;

    private String content;

    /** 状态：0待处理 1已回复 2已关闭 */
    private Integer status;

    private String replyContent;

    private LocalDateTime replyTime;

    private LocalDateTime createdTime;
}
