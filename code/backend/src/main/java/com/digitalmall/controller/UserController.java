package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.entity.UserAddress;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.UserAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户模块（接口文档：二、用户模块 资料/地址/收藏）
 * 已实现：收货地址 CRUD；资料/收藏为后续迭代
 */
@RestController
@RequestMapping("/api/user")
@RequireRole
@RequiredArgsConstructor
public class UserController {

    private final UserAddressService userAddressService;

    /** 查询个人资料（后续迭代） */
    @GetMapping("/profile")
    public Result<Void> profile() {
        return Result.success();
    }

    /** 修改资料（后续迭代） */
    @PutMapping("/profile")
    public Result<Void> updateProfile() {
        return Result.success();
    }

    /** 地址列表（默认优先） */
    @GetMapping("/address/list")
    public Result<List<UserAddress>> addressList() {
        return Result.success(userAddressService.list());
    }

    /** 新增地址 */
    @PostMapping("/address")
    public Result<Long> addAddress(@RequestBody UserAddress address) {
        return Result.success(userAddressService.add(address));
    }

    /** 修改地址 */
    @PutMapping("/address/{id}")
    public Result<Void> updateAddress(@PathVariable Long id, @RequestBody UserAddress address) {
        address.setId(id);
        userAddressService.update(address);
        return Result.success();
    }

    /** 删除地址 */
    @DeleteMapping("/address/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        userAddressService.delete(id);
        return Result.success();
    }

    /** 设为默认地址 */
    @PutMapping("/address/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable Long id) {
        userAddressService.setDefault(id);
        return Result.success();
    }

    /** 收藏列表（后续迭代） */
    @GetMapping("/favorite/list")
    public Result<Void> favoriteList() {
        return Result.success();
    }

    /** 添加收藏（后续迭代） */
    @PostMapping("/favorite")
    public Result<Void> addFavorite(@RequestParam Long productId) {
        return Result.success();
    }

    /** 取消收藏（后续迭代） */
    @DeleteMapping("/favorite/{productId}")
    public Result<Void> removeFavorite(@PathVariable Long productId) {
        return Result.success();
    }

    /** 是否已收藏（后续迭代） */
    @GetMapping("/favorite/check")
    public Result<Boolean> checkFavorite(@RequestParam Long productId) {
        return Result.success(false);
    }
}
