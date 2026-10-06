package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家本店订单列表项
 */
@Data
public class MerchantOrderVO {

    private Long id;

    private String orderNo;

    /** 0待付款 1待发货 2待收货 3已完成 4已取消 5售后中 */
    private Integer status;

    private BigDecimal payAmount;

    /** 商品件数 */
    private Integer itemCount;

    /** 首图 */
    private String firstImage;

    /** 收货人快照 */
    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    private LocalDateTime createdTime;
}
