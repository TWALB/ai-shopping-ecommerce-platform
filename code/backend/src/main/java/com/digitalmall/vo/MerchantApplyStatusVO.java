package com.digitalmall.vo;

import lombok.Data;

/**
 * 商家入驻申请状态
 */
@Data
public class MerchantApplyStatusVO {

    /** 0待审核 1通过 2拒绝 */
    private Integer status;

    private String rejectReason;

    /** 审核通过后的店铺ID */
    private Long shopId;

    /** 申请时间 */
    private String createdTime;
}
