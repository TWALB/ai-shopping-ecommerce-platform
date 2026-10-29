package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导购对话消息表
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    /** 0用户 1导购助手 */
    private Integer role;

    private String content;

    /** 推荐关联商品ID（逗号分隔） */
    private String productIds;

    /** 识别到的意图 */
    private String intent;

    private Integer tokenCount;

    private LocalDateTime createdTime;
}
