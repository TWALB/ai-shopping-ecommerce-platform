package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 可调用工具注册表。
 * 元数据（给模型看的 name/description/schema）与实现（Java handler）分离：
 * 运营能改描述、改参数说明、临时停用，但改不了代码，避免把执行逻辑暴露到管理端。
 */
@Data
@TableName("sys_ai_tool")
public class SysAiTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工具唯一编码，同时是模型看到的 function name */
    private String toolCode;

    private String toolName;

    /** product=商品域，biz=业务域（订单/物流/售后），决定出现在哪个调试台 */
    private String domain;

    /** 给模型看的用途说明，直接影响模型是否选它 */
    private String description;

    /** 入参 JSON Schema，调试台据此自动生成表单 */
    private String paramsSchema;

    /** 调试台一键填充的示例参数 */
    private String sampleParams;

    /** 返回结构说明，便于运营判断结果是否合理 */
    private String resultHint;

    private Integer enabled;

    /** 1=内置工具（有 Java 实现，不可删除） */
    private Integer builtin;

    private Integer sortNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
