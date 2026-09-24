package com.digitalmall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情（明细/收货快照/物流/支付）
 */
@Data
public class OrderDetailVO {

    private Long id;

    private String orderNo;

    private Long shopId;

    private String shopName;

    /** 0待付款 1待发货 2待收货 3已完成 4已取消 5售后中 */
    private Integer status;

    private BigDecimal totalAmount;

    private BigDecimal freightAmount;

    private BigDecimal payAmount;

    private String remark;

    private LocalDateTime createdTime;

    private LocalDateTime finishTime;

    /** 收货人快照 */
    private ReceiverVO receiver;

    /** 订单明细 */
    private List<ItemVO> items;

    /** 物流信息（发货后非空） */
    private LogisticsVO logistics;

    /** 支付信息（支付后非空） */
    private PaymentVO payment;

    @Data
    public static class ReceiverVO {
        private String name;
        private String phone;
        private String address;
    }

    @Data
    public static class ItemVO {
        private Long orderItemId;
        private Long productId;
        private String productName;
        private String skuName;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal totalAmount;
        private String productImage;
    }

    @Data
    public static class LogisticsVO {
        private String companyName;
        private String logisticsNo;
        private Integer status;
        private LocalDateTime deliveryTime;
    }

    @Data
    public static class PaymentVO {
        private String paymentNo;
        private Integer status;
        private LocalDateTime payTime;
    }
}
