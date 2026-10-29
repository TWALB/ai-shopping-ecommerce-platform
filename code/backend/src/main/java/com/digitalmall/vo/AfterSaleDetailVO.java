package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 售后单详情（含处理进度）
 */
@Data
public class AfterSaleDetailVO extends AfterSaleItemVO {

    private Long userId;

    private String username;

    /** 申请时订单状态 */
    private Integer orderStatus;

    private BigDecimal orderPayAmount;

    private String shopName;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    /** 处理进度 */
    private List<ProgressVO> progress;

    @Data
    public static class ProgressVO {
        private String title;
        private String description;
        private String time;
        private Boolean active;
    }
}
