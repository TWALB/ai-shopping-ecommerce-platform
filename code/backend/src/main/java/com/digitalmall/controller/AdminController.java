package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import com.digitalmall.dto.NoticeRequest;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.AdminService;
import com.digitalmall.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员模块（接口文档：十一、管理员 用户/审核/商品/订单/公告/统计）
 * 全部接口仅管理员可访问；关键操作写 operation_log
 */
@RestController
@RequestMapping("/api/admin")
@RequireRole(RequireRole.ADMIN)
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** 用户管理分页 */
    @GetMapping("/user/page")
    public Result<PageResult<UserAdminVO>> userPage(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer role,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(defaultValue = "1") long pageNum,
                                                    @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminService.userPage(keyword, role, status, pageNum, pageSize));
    }

    /** 启用/禁用用户（禁用同时冻结其店铺） */
    @PutMapping("/user/{id}/status")
    public Result<Void> userStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.userStatus(id, status);
        return Result.success();
    }

    /** 调整用户角色 */
    @PutMapping("/user/{id}/role")
    public Result<Void> userRole(@PathVariable Long id, @RequestParam Integer role) {
        adminService.userRole(id, role);
        return Result.success();
    }

    /** 入驻申请列表 */
    @GetMapping("/merchant-apply/page")
    public Result<PageResult<MerchantApplyAdminVO>> applyPage(@RequestParam(required = false) Integer status,
                                                              @RequestParam(defaultValue = "1") long pageNum,
                                                              @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminService.applyPage(status, pageNum, pageSize));
    }

    /** 审核入驻（通过自动开店铺 + 用户 role=1） */
    @PutMapping("/merchant-apply/{id}/audit")
    public Result<Void> auditApply(@PathVariable Long id, @RequestParam Boolean agree,
                                   @RequestParam(required = false) String rejectReason) {
        adminService.auditApply(id, agree, rejectReason);
        return Result.success();
    }

    /** 平台商品管理 */
    @GetMapping("/product/page")
    public Result<PageResult<AdminProductVO>> productPage(@RequestParam(required = false) Integer status,
                                                          @RequestParam(defaultValue = "1") long pageNum,
                                                          @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminService.productPage(status, pageNum, pageSize));
    }

    /** 平台级商品上下架 */
    @PutMapping("/product/{id}/status")
    public Result<Void> productStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.productStatus(id, status);
        return Result.success();
    }

    /** 平台订单管理 */
    @GetMapping("/order/page")
    public Result<PageResult<OrderVO>> orderPage(@RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String orderNo,
                                                 @RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminService.orderPage(status, orderNo, pageNum, pageSize));
    }

    /** 售后管理 */
    @GetMapping("/after-sale/page")
    public Result<PageResult<AfterSaleAdminVO>> afterSalePage(@RequestParam(required = false) Integer status,
                                                              @RequestParam(defaultValue = "1") long pageNum,
                                                              @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminService.afterSalePage(status, pageNum, pageSize));
    }

    /** 发布公告 */
    @PostMapping("/notice")
    public Result<Void> addNotice(@RequestBody NoticeRequest request) {
        adminService.addNotice(request);
        return Result.success();
    }

    /** 公告列表 */
    @GetMapping("/notice/page")
    public Result<PageResult<NoticeVO>> noticePage(@RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminService.noticePage(status, pageNum, pageSize));
    }

    /** 删除公告 */
    @DeleteMapping("/notice/{id}")
    public Result<Void> deleteNotice(@PathVariable Long id) {
        adminService.deleteNotice(id);
        return Result.success();
    }

    /** 平台数据总览 */
    @GetMapping("/stats/overview")
    public Result<StatsOverviewVO> statsOverview() {
        return Result.success(adminService.statsOverview());
    }

    /** 交易趋势（近7/30日） */
    @GetMapping("/stats/trend")
    public Result<List<TrendPointVO>> statsTrend(@RequestParam(defaultValue = "7") Integer days) {
        return Result.success(adminService.statsTrend(days));
    }

    /** 操作日志 */
    @GetMapping("/operation-log/page")
    public Result<PageResult<OperationLogVO>> operationLogPage(@RequestParam(required = false) String module,
                                                               @RequestParam(required = false) String username,
                                                               @RequestParam(defaultValue = "1") long pageNum,
                                                               @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(adminService.operationLogPage(module, username, pageNum, pageSize));
    }
}
