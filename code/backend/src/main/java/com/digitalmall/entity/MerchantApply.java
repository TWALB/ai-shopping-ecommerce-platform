package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家入驻申请表
 */
@Data
@TableName("merchant_apply")
public class MerchantApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人用户ID（sys_user.id） */
    private Long userId;

    private String realName;

    private String idCard;

    private String contactPhone;

    /** 拟开设店铺名称 */
    private String shopName;

    private String shopIntro;

    private String qualificationUrl;

    /** 审核状态：0待审核 1通过 2拒绝 */
    private Integer status;

    private String rejectReason;

    /** 审核管理员ID */
    private Long auditBy;

    private LocalDateTime auditTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
