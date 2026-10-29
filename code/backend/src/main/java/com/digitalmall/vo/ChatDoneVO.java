package com.digitalmall.vo;

import lombok.Data;

/**
 * 导购消息完成事件
 */
@Data
public class ChatDoneVO {

    private Long sessionId;

    private Long messageId;

    private String intent;

    private Integer tokenCount;
}
