package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 提示词与运行参数配置（运营可在管理后台修改，保存后热生效，无需重启）
 */
@Data
@TableName("sys_ai_config")
public class SysAiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键，如 chat.system_prompt */
    private String configKey;

    /** 展示名称 */
    private String configName;

    /** 当前值；提示词支持 {{变量}} 占位符 */
    private String configValue;

    /** 内置默认值，用于一键恢复 */
    private String defaultValue;

    /** 说明与可用变量 */
    private String description;

    /** 保存一次自增，用于展示与后续回滚 */
    private Integer version;

    /** 最后修改人（管理员 ID） */
    private Long updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
