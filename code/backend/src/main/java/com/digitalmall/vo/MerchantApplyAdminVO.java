package com.digitalmall.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入驻申请审核列表项
 */
@Data
public class MerchantApplyAdminVO {

    private Long id;

    private String realName;

    private String idCard;

    private String contactPhone;

    private String shopName;

    private String shopIntro;

    private String qualificationUrl;

    /** 0待审核 1通过 2拒绝 */
    private Integer status;

    private String rejectReason;

    /** 申请人账号 */
    private String username;

    private String nickname;

    private LocalDateTime createdTime;
}
