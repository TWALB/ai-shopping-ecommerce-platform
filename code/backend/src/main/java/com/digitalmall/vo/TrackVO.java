package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流轨迹项
 */
@Data
public class TrackVO {

    private String trackInfo;

    private LocalDateTime trackTime;
}
