package com.digitalmall.service;

import com.digitalmall.vo.PaymentVO;

/**
 * 支付服务（模拟支付）
 */
public interface PaymentService {

    /** 模拟支付：校验归属与待付款 → 写流水 → 订单 0→1 */
    PaymentVO pay(Long orderId);

    /** 支付结果查询 */
    PaymentVO query(Long orderId);
}
