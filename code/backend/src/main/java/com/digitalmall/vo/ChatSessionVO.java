package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导购会话列表项
 */
@Data
public class ChatSessionVO {

    private Long id;

    private String sessionTitle;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
