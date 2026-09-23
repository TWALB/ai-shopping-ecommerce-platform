package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 购物车模块（接口文档：四、购物车）
 * 开发阶段按接口文档逐个实现
 */
@RestController
@RequestMapping("/api/cart")
@RequireRole
public class CartController {

    /** 购物车列表 */
    @GetMapping("/list")
    public Result<Void> list() {
        // TODO
        return Result.success();
    }

    /** 加入购物车 */
    @PostMapping
    public Result<Void> add(@RequestParam Long skuId, @RequestParam Integer quantity) {
        // TODO 已存在则累加数量；超库存返回 409
        return Result.success();
    }

    /** 修改数量 */
    @PutMapping("/{id}/quantity")
    public Result<Void> updateQuantity(@PathVariable Long id, @RequestParam Integer quantity) {
        // TODO
        return Result.success();
    }

    /** 勾选/取消勾选 */
    @PutMapping("/{id}/selected")
    public Result<Void> updateSelected(@PathVariable Long id, @RequestParam Boolean isSelected) {
        // TODO
        return Result.success();
    }

    /** 删除购物车项 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 下单后清空勾选项 */
    @PostMapping("/clear-selected")
    public Result<Void> clearSelected() {
        // TODO
        return Result.success();
    }
}
