package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户咨询/投诉表（商家端客户管理）
 */
@Data
@TableName("complaint")
public class Complaint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起用户ID */
    private Long userId;

    /** 店铺ID */
    private Long shopId;

    /** 关联订单ID（可空） */
    private Long orderId;

    /** 类型：0咨询 1投诉 */
    private Integer type;

    private String content;

    /** 状态：0待处理 1已回复 2已关闭 */
    private Integer status;

    private String replyContent;

    private LocalDateTime replyTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
