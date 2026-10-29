package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导购历史消息项
 */
@Data
public class ChatMessageVO {

    private Long id;

    /** 0用户 1导购助手 */
    private Integer role;

    private String content;

    private List<Long> productIds;

    /** 推荐商品详情（前端渲染卡片） */
    private List<ProductBriefVO> products;

    private String intent;

    private LocalDateTime createdTime;
}
