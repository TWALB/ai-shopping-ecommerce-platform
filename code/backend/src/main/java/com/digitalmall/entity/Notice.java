package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统公告表
 */
@Data
@TableName("notice")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** 类型：0活动 1商品上新 2平台规则 */
    private Integer type;

    /** 状态：0草稿 1已发布 */
    private Integer status;

    private LocalDateTime publishTime;

    /** 发布人ID（管理员） */
    private Long publisherId;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
