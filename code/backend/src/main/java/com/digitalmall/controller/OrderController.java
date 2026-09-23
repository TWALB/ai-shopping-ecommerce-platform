package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单模块（接口文档：五、订单）
 * 开发阶段按接口文档逐个实现
 */
@RestController
@RequestMapping("/api/order")
@RequireRole
public class OrderController {

    /** 创建订单（库存扣减 + 快照，核心交易接口） */
    @PostMapping
    public Result<Void> create(@RequestBody Map<String, Object> body) {
        // TODO body: { addressId, fromCart, itemList:[{skuId,quantity}], remark }
        return Result.success();
    }

    /** 我的订单列表 */
    @GetMapping("/page")
    public Result<Void> page(@RequestParam(required = false) Integer status,
                             @RequestParam(defaultValue = "1") long pageNum,
                             @RequestParam(defaultValue = "10") long pageSize) {
        // TODO
        return Result.success();
    }

    /** 订单详情 */
    @GetMapping("/{id}")
    public Result<Void> detail(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 取消订单（仅待付款，回补库存） */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 确认收货 */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 物流跟踪 */
    @GetMapping("/{id}/track")
    public Result<Void> track(@PathVariable Long id) {
        // TODO
        return Result.success();
    }
}
