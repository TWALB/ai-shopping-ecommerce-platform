package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流表
 */
@Data
@TableName("logistics")
public class Logistics {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String logisticsNo;

    private String companyName;

    /** 状态：0已揽收 1运输中 2已签收 */
    private Integer status;

    private LocalDateTime deliveryTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
