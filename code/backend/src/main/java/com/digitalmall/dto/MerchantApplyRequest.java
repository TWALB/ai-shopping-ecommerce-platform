package com.digitalmall.dto;

import lombok.Data;

import java.util.List;

/**
 * 提交商家入驻申请
 */
@Data
public class MerchantApplyRequest {

    private String realName;

    private String idCard;

    private String contactPhone;

    /** 拟开设店铺名称 */
    private String shopName;

    private String shopIntro;

    private String qualificationUrl;
}
