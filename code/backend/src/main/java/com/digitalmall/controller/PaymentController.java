package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 支付模块（接口文档：六、支付 模拟支付）
 */
@RestController
@RequestMapping("/api/payment")
@RequireRole
public class PaymentController {

    /** 发起模拟支付（点击支付直接成功） */
    @PostMapping
    public Result<Void> pay(@RequestParam Long orderId) {
        // TODO 校验订单归属与待付款状态 → 写 payment 流水 → 订单 0→1
        return Result.success();
    }

    /** 支付结果查询 */
    @GetMapping("/query")
    public Result<Void> query(@RequestParam Long orderId) {
        // TODO
        return Result.success();
    }
}
