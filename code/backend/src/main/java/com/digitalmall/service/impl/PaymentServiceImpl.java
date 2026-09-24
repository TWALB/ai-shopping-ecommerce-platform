package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.common.BusinessException;
import com.digitalmall.entity.Order;
import com.digitalmall.entity.Payment;
import com.digitalmall.mapper.OrderMapper;
import com.digitalmall.mapper.PaymentMapper;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.PaymentService;
import com.digitalmall.vo.PaymentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务实现（模拟支付）
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;

    private Long uid() {
        return UserContext.getUserId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO pay(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权支付该订单");
        }

        // 乐观更新：待付款→待发货，防止重复支付
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, 1)
                .set(Order::getPayType, 0)
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 0));
        if (updated == 0) {
            throw new BusinessException("订单状态不允许支付");
        }

        // 写支付流水
        Payment payment = new Payment();
        payment.setPaymentNo(genPaymentNo());
        payment.setOrderId(orderId);
        payment.setUserId(uid());
        payment.setAmount(order.getPayAmount());
        payment.setPayType(0);
        payment.setStatus(1);
        payment.setPayTime(LocalDateTime.now());
        paymentMapper.insert(payment);

        PaymentVO vo = new PaymentVO();
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setStatus(1);
        vo.setPayTime(payment.getPayTime());
        return vo;
    }

    @Override
    public PaymentVO query(Long orderId) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .orderByDesc(Payment::getId)
                .last("limit 1"));
        if (payment == null) {
            return null;
        }
        PaymentVO vo = new PaymentVO();
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setStatus(payment.getStatus());
        vo.setPayTime(payment.getPayTime());
        return vo;
    }

    private String genPaymentNo() {
        return "P" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }
}
