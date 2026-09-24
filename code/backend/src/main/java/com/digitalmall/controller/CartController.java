package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.CartService;
import com.digitalmall.vo.CartVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车模块（接口文档：四、购物车）— 用户接口
 */
@RestController
@RequestMapping("/api/cart")
@RequireRole
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 购物车列表（含商品与SKU信息） */
    @GetMapping("/list")
    public Result<List<CartVO>> list() {
        return Result.success(cartService.list());
    }

    /** 加入购物车（已存在则数量累加，超库存 409） */
    @PostMapping
    public Result<Void> add(@RequestParam Long skuId, @RequestParam Integer quantity) {
        cartService.add(skuId, quantity);
        return Result.success();
    }

    /** 修改购物车项数量 */
    @PutMapping("/{id}/quantity")
    public Result<Void> updateQuantity(@PathVariable Long id, @RequestParam Integer quantity) {
        cartService.updateQuantity(id, quantity);
        return Result.success();
    }

    /** 勾选/取消勾选 */
    @PutMapping("/{id}/selected")
    public Result<Void> updateSelected(@PathVariable Long id, @RequestParam Boolean isSelected) {
        cartService.updateSelected(id, isSelected);
        return Result.success();
    }

    /** 删除购物车项 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cartService.delete(id);
        return Result.success();
    }

    /** 下单后清空勾选项（由前端在下单成功回调中调用） */
    @PostMapping("/clear-selected")
    public Result<Void> clearSelected() {
        cartService.clearSelected();
        return Result.success();
    }
}
