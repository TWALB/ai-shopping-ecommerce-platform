package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import com.digitalmall.dto.SubmitCommentRequest;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.CommentService;
import com.digitalmall.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评价模块（接口文档：七、评价）
 * 列表公开；发表评价需登录；管理接口按角色标注
 */
@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** 发表评价（用户，订单完成后；同一明细仅可评一次） */
    @PostMapping
    @RequireRole
    public Result<Void> add(@RequestBody SubmitCommentRequest request) {
        commentService.submit(request);
        return Result.success();
    }

    /** 商品已展示评价列表（公开，分页） */
    @GetMapping("/product/{productId}")
    public Result<PageResult<CommentVO>> productComments(@PathVariable Long productId,
                                                         @RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(commentService.productComments(productId, pageNum, pageSize));
    }

    /** 评价管理分页（商家看本店 / 管理员看全部） */
    @GetMapping("/page")
    @RequireRole({RequireRole.MERCHANT, RequireRole.ADMIN})
    public Result<PageResult<CommentVO>> page(@RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String productName,
                                              @RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(commentService.managePage(status, productName, pageNum, pageSize));
    }

    /** 商家回复评价 */
    @PutMapping("/{id}/reply")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> reply(@PathVariable Long id, @RequestParam String replyContent) {
        commentService.reply(id, replyContent);
        return Result.success();
    }

    /** 管理员审核评价（1展示 / 2违规删除） */
    @PutMapping("/{id}/status")
    @RequireRole(RequireRole.ADMIN)
    public Result<Void> audit(@PathVariable Long id, @RequestParam Integer status) {
        commentService.audit(id, status);
        return Result.success();
    }
}
