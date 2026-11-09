package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端「AI 配置中心」返回结构：既是当前值，也带默认值与可用变量，
 * 前端据此渲染编辑框、变量提示与「恢复默认」。
 */
@Data
public class AiConfigVO {

    private String key;

    private String name;

    private String value;

    private String defaultValue;

    private String description;

    /** 当前模板里用到的占位符，如 productCount */
    private List<String> variables;

    private Integer version;

    private LocalDateTime updateTime;

    private Long updatedBy;
}
