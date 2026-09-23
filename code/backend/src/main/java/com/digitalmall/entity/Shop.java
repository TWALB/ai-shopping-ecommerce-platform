package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺表
 */
@Data
@TableName("shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商家用户ID（sys_user.id） */
    private Long merchantId;

    private String shopName;

    private String shopLogo;

    private String shopBanner;

    private String shopIntro;

    /** 0冻结 1正常 */
    private Integer status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
