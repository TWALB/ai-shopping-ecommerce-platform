package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志列表项
 */
@Data
public class OperationLogVO {

    private Long id;

    private String username;

    private String module;

    private String action;

    private String ip;

    private LocalDateTime createdTime;
}
