package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流轨迹表
 */
@Data
@TableName("logistics_track")
public class LogisticsTrack {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long logisticsId;

    private String trackInfo;

    private LocalDateTime trackTime;
}
