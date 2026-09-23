package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品牌表
 */
@Data
@TableName("brand")
public class Brand {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String brandName;

    private String brandLogo;

    private String brandDesc;

    /** 0停用 1启用 */
    private Integer status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
