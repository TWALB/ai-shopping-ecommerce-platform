package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.security.RequireRole;
import org.springframework.web.bind.annotation.*;

/**
 * 商家模块（接口文档：十、商家 入驻/店铺/商品/发货/统计）
 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    /** 提交入驻申请（普通用户） */
    @PostMapping("/apply")
    @RequireRole(RequireRole.USER)
    public Result<Void> apply() {
        // TODO 同一用户仅一条待审申请（409）；审核通过后自动开店铺
        return Result.success();
    }

    /** 查询我的入驻申请状态 */
    @GetMapping("/apply/status")
    @RequireRole
    public Result<Void> applyStatus() {
        // TODO
        return Result.success();
    }

    /** 我的店铺信息 */
    @GetMapping("/shop")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> shop() {
        // TODO
        return Result.success();
    }

    /** 修改店铺信息 */
    @PutMapping("/shop")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> updateShop() {
        // TODO
        return Result.success();
    }

    /** 我的商品列表 */
    @GetMapping("/product/page")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> productPage(@RequestParam(required = false) Integer status) {
        // TODO
        return Result.success();
    }

    /** 发布商品（商品+SKU+参数+图片一次提交） */
    @PostMapping("/product")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> addProduct() {
        // TODO 发布后 status=0 草稿；触发导购知识库增量同步
        return Result.success();
    }

    /** 编辑商品 */
    @PutMapping("/product/{id}")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> updateProduct(@PathVariable Long id) {
        // TODO
        return Result.success();
    }

    /** 商品上下架 */
    @PutMapping("/product/{id}/status")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> updateProductStatus(@PathVariable Long id, @RequestParam Integer status) {
        // TODO 下架同步删除 Redis 向量；上架同步添加
        return Result.success();
    }

    /** 本店订单列表 */
    @GetMapping("/order/page")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> orderPage(@RequestParam(required = false) Integer status) {
        // TODO
        return Result.success();
    }

    /** 发货 */
    @PostMapping("/order/{id}/deliver")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> deliver(@PathVariable Long id, @RequestParam String logisticsNo,
                                @RequestParam String companyName) {
        // TODO 写 logistics + 首条轨迹；订单 1→2
        return Result.success();
    }

    /** 经营数据统计 */
    @GetMapping("/stats")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> stats() {
        // TODO 销量/销售额/访问量（聚合 order、product.sales、view_count）
        return Result.success();
    }
}
