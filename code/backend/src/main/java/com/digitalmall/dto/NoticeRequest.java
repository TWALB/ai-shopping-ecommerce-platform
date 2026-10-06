package com.digitalmall.dto;

import lombok.Data;

/**
 * 发布系统公告
 */
@Data
public class NoticeRequest {

    private String title;

    private String content;

    /** 0活动 1商品上新 2平台规则 */
    private Integer type;

    /** 发布时间（空=立即发布） */
    private String publishTime;
}
