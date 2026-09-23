package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员模块（接口文档：十一、管理员 用户/审核/商品/公告/统计）
 * 全部接口仅管理员可访问；关键操作写 operation_log
 */
@RestController
@RequestMapping("/api/admin")
@RequireRole(RequireRole.ADMIN)
public class AdminController {

    /** 用户管理分页 */
    @GetMapping("/user/page")
    public Result<Void> userPage(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) Integer role,
                                 @RequestParam(required = false) Integer status) {
        // TODO
        return Result.success();
    }

    /** 启用/禁用用户（禁用同时冻结其店铺） */
    @PutMapping("/user/{id}/status")
    public Result<Void> userStatus(@PathVariable Long id, @RequestParam Integer status) {
        // TODO
        return Result.success();
    }

    /** 调整用户角色 */
    @PutMapping("/user/{id}/role")
    public Result<Void> userRole(@PathVariable Long id, @RequestParam Integer role) {
        // TODO
        return Result.success();
    }

    /** 入驻申请列表 */
    @GetMapping("/merchant-apply/page")
    public Result<Void> applyPage() {
        // TODO
        return Result.success();
    }

    /** 审核入驻（通过自动开店铺 + 用户 role=1） */
    @PutMapping("/merchant-apply/{id}/audit")
    public Result<Void> auditApply(@PathVariable Long id, @RequestParam Boolean agree,
                                   @RequestParam(required = false) String rejectReason) {
        // TODO
        return Result.success();
    }

    /** 平台商品管理 */
    @GetMapping("/product/page")
    public Result<Void> productPage(@RequestParam(required = false) Integer status) {
        // TODO
        return Result.success();
    }

    /** 平台级商品上下架 */
    @PutMapping("/product/{id}/status")
    public Result<Void> productStatus(@PathVariable Long id, @RequestParam Integer status) {
        // TODO
        return Result.success();
    }

    /** 平台订单管理 */
    @GetMapping("/order/page")
    public Result<Void> orderPage(@RequestParam(required = false) Integer status,
                                  @RequestParam(required = false) String orderNo) {
        // TODO
        return Result.success();
    }

    /** 售后管理 */
    @GetMapping("/after-sale/page")
    public Result<Void> afterSalePage() {
        // TODO
        return Result.success();
    }

    /** 发布公告 */
    @PostMapping("/notice")
    public Result<Void> addNotice() {
        // TODO
        return Result.success();
    }

    /** 公告列表 */
    @GetMapping("/notice/page")
    public Result<Void> noticePage() {
        // TODO
        return Result.success();
    }

    /** 删除公告 */
    @DeleteMapping("/notice/{id}")
    public Result<Void> deleteNotice(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 平台数据总览 */
    @GetMapping("/stats/overview")
    public Result<Void> statsOverview() {
        // TODO 用户数/商品数/订单数/交易额
        return Result.success();
    }

    /** 交易趋势（近7/30日） */
    @GetMapping("/stats/trend")
    public Result<Void> statsTrend(@RequestParam(defaultValue = "7") Integer days) {
        // TODO [{date, orderCount, amount}]
        return Result.success();
    }

    /** 操作日志 */
    @GetMapping("/operation-log/page")
    public Result<Void> operationLogPage(@RequestParam(required = false) String module) {
        // TODO
        return Result.success();
    }
}
