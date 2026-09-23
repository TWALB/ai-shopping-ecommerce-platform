package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 售后模块（接口文档：八、售后）
 */
@RestController
@RequestMapping("/api/after-sale")
public class AfterSaleController {

    /** 提交售后申请（用户） */
    @PostMapping
    @RequireRole
    public Result<Void> apply() {
        // TODO 申请后订单 status→5（售后中）
        return Result.success();
    }

    /** 我的售后列表（用户） */
    @GetMapping("/page")
    @RequireRole
    public Result<Void> myPage() {
        // TODO
        return Result.success();
    }

    /** 售后详情（用户） */
    @GetMapping("/{id}")
    @RequireRole
    public Result<Void> detail(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 撤销申请（用户，仅待商家处理） */
    @PostMapping("/{id}/cancel")
    @RequireRole
    public Result<Void> cancel(@PathVariable Long id) {
        // TODO 撤销后订单恢复售后前状态
        return Result.success();
    }

    /** 商家处理售后 */
    @PutMapping("/{id}/handle")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> handle(@PathVariable Long id, @RequestParam Boolean agree,
                               @RequestParam(required = false) String rejectReason) {
        // TODO
        return Result.success();
    }

    /** 管理员介入处理 */
    @PutMapping("/{id}/admin")
    @RequireRole(RequireRole.ADMIN)
    public Result<Void> adminHandle(@PathVariable Long id, @RequestParam Integer status,
                                    @RequestParam(required = false) String remark) {
        // TODO
        return Result.success();
    }
}
