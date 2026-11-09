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

    /** 场景：model / chat / intent / param，决定在哪个页面展示 */
    private String scene;

    /** 是否密钥类配置：true 时 value 为掩码，提交回掩码视为未修改 */
    private Boolean secret;

    private String value;

    private String defaultValue;

    private String description;

    /** 当前模板里用到的占位符，如 productCount */
    private List<String> variables;

    private Integer version;

    private LocalDateTime updateTime;

    private Long updatedBy;

    /** 是否已写入数据库（false 表示当前用的是内置默认值） */
    private Boolean configured;

    /** 保存后返回的提示，例如「更换向量模型后需重建向量库」 */
    private String warning;
}
