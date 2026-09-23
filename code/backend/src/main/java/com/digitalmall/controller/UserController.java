package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 用户模块（接口文档：二、用户模块 资料/地址/收藏）
 * 开发阶段按接口文档逐个实现
 */
@RestController
@RequestMapping("/api/user")
@RequireRole
public class UserController {

    /** 查询个人资料 */
    @GetMapping("/profile")
    public Result<Void> profile() {
        // TODO
        return Result.success();
    }

    /** 修改资料 */
    @PutMapping("/profile")
    public Result<Void> updateProfile() {
        // TODO
        return Result.success();
    }

    /** 地址列表 */
    @GetMapping("/address/list")
    public Result<Void> addressList() {
        // TODO
        return Result.success();
    }

    /** 新增地址 */
    @PostMapping("/address")
    public Result<Void> addAddress() {
        // TODO
        return Result.success();
    }

    /** 修改地址 */
    @PutMapping("/address/{id}")
    public Result<Void> updateAddress(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 删除地址 */
    @DeleteMapping("/address/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 设为默认地址 */
    @PutMapping("/address/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 收藏列表 */
    @GetMapping("/favorite/list")
    public Result<Void> favoriteList() {
        // TODO
        return Result.success();
    }

    /** 添加收藏 */
    @PostMapping("/favorite")
    public Result<Void> addFavorite(@RequestParam Long productId) {
        // TODO
        return Result.success();
    }

    /** 取消收藏 */
    @DeleteMapping("/favorite/{productId}")
    public Result<Void> removeFavorite(@PathVariable Long productId) {
        // TODO
        return Result.success();
    }

    /** 是否已收藏 */
    @GetMapping("/favorite/check")
    public Result<Boolean> checkFavorite(@RequestParam Long productId) {
        // TODO
        return Result.success(false);
    }
}
