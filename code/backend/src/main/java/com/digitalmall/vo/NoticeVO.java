package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告列表项
 */
@Data
public class NoticeVO {

    private Long id;

    private String title;

    private String content;

    /** 0活动 1商品上新 2平台规则 */
    private Integer type;

    /** 0草稿 1已发布 */
    private Integer status;

    private LocalDateTime publishTime;

    private String publisherName;

    private LocalDateTime createdTime;
}
