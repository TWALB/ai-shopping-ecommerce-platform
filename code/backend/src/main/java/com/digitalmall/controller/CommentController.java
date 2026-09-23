package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 评价模块（接口文档：七、评价）
 * 注意：评价列表公开，管理接口按角色标注
 */
@RestController
@RequestMapping("/api/comment")
public class CommentController {

    /** 发表评价（用户，订单完成后） */
    @PostMapping
    @RequireRole
    public Result<Void> add() {
        // TODO 同一 order_item 仅可评价一次（409）
        return Result.success();
    }

    /** 商品已展示评价列表（公开） */
    @GetMapping("/product/{productId}")
    public Result<Void> productComments(@PathVariable Long productId) {
        // TODO
        return Result.success();
    }

    /** 评价管理分页（商家看本店 / 管理员看全部） */
    @GetMapping("/page")
    @RequireRole({RequireRole.MERCHANT, RequireRole.ADMIN})
    public Result<Void> page(@RequestParam(required = false) Integer status) {
        // TODO
        return Result.success();
    }

    /** 商家回复评价 */
    @PutMapping("/{id}/reply")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> reply(@PathVariable Long id, @RequestParam String replyContent) {
        // TODO
        return Result.success();
    }

    /** 管理员审核评价（展示/违规删除） */
    @PutMapping("/{id}/status")
    @RequireRole(RequireRole.ADMIN)
    public Result<Void> audit(@PathVariable Long id, @RequestParam Integer status) {
        // TODO
        return Result.success();
    }
}
