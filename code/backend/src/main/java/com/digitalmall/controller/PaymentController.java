package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.PaymentService;
import com.digitalmall.vo.PaymentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 支付模块（接口文档：六、支付 模拟支付）— 用户接口
 */
@RestController
@RequestMapping("/api/payment")
@RequireRole
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 模拟支付（点击支付直接成功） */
    @PostMapping
    public Result<PaymentVO> pay(@RequestParam Long orderId) {
        return Result.success(paymentService.pay(orderId));
    }

    /** 查询订单支付结果 */
    @GetMapping("/query")
    public Result<PaymentVO> query(@RequestParam Long orderId) {
        return Result.success(paymentService.query(orderId));
    }
}
