package com.digitalmall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交咨询/投诉请求
 */
@Data
public class ComplaintCreateRequest {

    /** 目标店铺 */
    @NotNull(message = "请选择店铺")
    private Long shopId;

    /** 关联订单（可空） */
    private Long orderId;

    /** 类型：0咨询 1投诉 */
    @NotNull(message = "请选择类型")
    private Integer type;

    @NotBlank(message = "请填写内容")
    @Size(max = 1000, message = "内容不能超过 1000 字")
    private String content;
}
