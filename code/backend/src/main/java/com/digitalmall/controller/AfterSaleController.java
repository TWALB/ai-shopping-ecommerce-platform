package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import com.digitalmall.dto.AfterSaleApplyRequest;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.AfterSaleService;
import com.digitalmall.vo.AfterSaleDetailVO;
import com.digitalmall.vo.AfterSaleItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 售后模块（接口文档：八、售后）
 * 状态机：0待商家处理 → 1商家同意 / 2商家拒绝 / 3平台介入 → 4已完成；0 可撤销为 5
 */
@RestController
@RequestMapping("/api/after-sale")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    /** 提交售后申请（用户） */
    @PostMapping
    @RequireRole
    public Result<Void> apply(@Valid @RequestBody AfterSaleApplyRequest request) {
        afterSaleService.apply(request);
        return Result.success();
    }

    /** 我的售后列表（用户，可按状态/订单过滤） */
    @GetMapping("/page")
    @RequireRole
    public Result<PageResult<AfterSaleItemVO>> myPage(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(afterSaleService.myPage(status, orderId, pageNum, pageSize));
    }

    /** 售后详情（含处理进度） */
    @GetMapping("/{id}")
    @RequireRole
    public Result<AfterSaleDetailVO> detail(@PathVariable Long id) {
        return Result.success(afterSaleService.detail(id));
    }

    /** 撤销售后（仅待商家处理） */
    @PostMapping("/{id}/cancel")
    @RequireRole
    public Result<Void> cancel(@PathVariable Long id) {
        afterSaleService.cancel(id);
        return Result.success();
    }

    /** 商家处理售后（同意即完成退款；拒绝需填原因） */
    @PutMapping("/{id}/handle")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> handle(@PathVariable Long id,
                               @RequestParam Boolean agree,
                               @RequestParam(required = false) String rejectReason) {
        afterSaleService.merchantHandle(id, Boolean.TRUE.equals(agree), rejectReason);
        return Result.success();
    }

    /** 管理员介入处理（3平台介入中 / 4已完成） */
    @PutMapping("/{id}/admin")
    @RequireRole(RequireRole.ADMIN)
    public Result<Void> adminHandle(@PathVariable Long id,
                                    @RequestParam Integer status,
                                    @RequestParam(required = false) String remark) {
        afterSaleService.adminHandle(id, status, remark);
        return Result.success();
    }
}
