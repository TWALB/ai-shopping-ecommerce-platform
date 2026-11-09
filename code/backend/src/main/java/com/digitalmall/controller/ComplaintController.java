package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import com.digitalmall.dto.ComplaintCreateRequest;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.ComplaintService;
import com.digitalmall.vo.ComplaintVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 咨询/投诉模块（客户管理：用户提交 → 商家回复）
 */
@RestController
@RequestMapping("/api/complaint")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    /** 提交咨询/投诉（用户） */
    @PostMapping
    @RequireRole
    public Result<Void> create(@Valid @RequestBody ComplaintCreateRequest request) {
        complaintService.create(request);
        return Result.success();
    }

    /** 我提交的咨询/投诉（用户） */
    @GetMapping("/page")
    @RequireRole
    public Result<PageResult<ComplaintVO>> myPage(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(complaintService.myPage(status, pageNum, pageSize));
    }

    /** 本店收到的咨询/投诉（商家） */
    @GetMapping("/merchant/page")
    @RequireRole(RequireRole.MERCHANT)
    public Result<PageResult<ComplaintVO>> merchantPage(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(complaintService.merchantPage(status, pageNum, pageSize));
    }

    /** 商家回复（待处理 → 已回复） */
    @PutMapping("/{id}/reply")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> reply(@PathVariable Long id, @RequestParam String content) {
        complaintService.reply(id, content);
        return Result.success();
    }
}
