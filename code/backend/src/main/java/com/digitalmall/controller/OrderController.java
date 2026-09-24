package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import com.digitalmall.dto.CreateOrderRequest;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.OrderService;
import com.digitalmall.vo.CreateOrderVO;
import com.digitalmall.vo.OrderDetailVO;
import com.digitalmall.vo.OrderVO;
import com.digitalmall.vo.TrackVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单模块（接口文档：五、订单）— 用户接口
 */
@RestController
@RequestMapping("/api/order")
@RequireRole
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建订单（核心交易接口：库存扣减 + 快照 + 按店铺分单） */
    @PostMapping
    public Result<List<CreateOrderVO>> create(@RequestBody CreateOrderRequest request) {
        return Result.success(orderService.create(request));
    }

    /** 我的订单列表（按状态分类） */
    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(orderService.page(status, pageNum, pageSize));
    }

    /** 订单详情（明细/收货快照/物流/支付） */
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(id));
    }

    /** 取消订单（仅待付款，回补库存） */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.success();
    }

    /** 确认收货（待收货→已完成） */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        orderService.confirm(id);
        return Result.success();
    }

    /** 物流轨迹（时间倒序） */
    @GetMapping("/{id}/track")
    public Result<List<TrackVO>> track(@PathVariable Long id) {
        return Result.success(orderService.track(id));
    }
}
