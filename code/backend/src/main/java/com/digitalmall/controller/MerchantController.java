package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import com.digitalmall.dto.MerchantApplyRequest;
import com.digitalmall.dto.ProductPublishRequest;
import com.digitalmall.dto.ShopUpdateRequest;
import com.digitalmall.entity.Shop;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.MerchantService;
import com.digitalmall.vo.MerchantApplyStatusVO;
import com.digitalmall.vo.MerchantOrderVO;
import com.digitalmall.vo.MerchantProductVO;
import com.digitalmall.vo.MerchantStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商家模块（接口文档：十、商家 入驻/店铺/商品/发货/统计）
 */
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    /** 提交入驻申请（普通用户） */
    @PostMapping("/apply")
    @RequireRole(RequireRole.USER)
    public Result<Void> apply(@RequestBody MerchantApplyRequest request) {
        merchantService.apply(request);
        return Result.success();
    }

    /** 查询我的入驻申请状态 */
    @GetMapping("/apply/status")
    @RequireRole
    public Result<MerchantApplyStatusVO> applyStatus() {
        return Result.success(merchantService.applyStatus());
    }

    /** 我的店铺信息 */
    @GetMapping("/shop")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Shop> shop() {
        return Result.success(merchantService.shop());
    }

    /** 修改店铺信息 */
    @PutMapping("/shop")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> updateShop(@RequestBody ShopUpdateRequest request) {
        merchantService.updateShop(request);
        return Result.success();
    }

    /** 我的商品列表（按状态筛选） */
    @GetMapping("/product/page")
    @RequireRole(RequireRole.MERCHANT)
    public Result<PageResult<MerchantProductVO>> productPage(@RequestParam(required = false) Integer status,
                                                             @RequestParam(defaultValue = "1") long pageNum,
                                                             @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(merchantService.productPage(status, pageNum, pageSize));
    }

    /** 发布商品（商品+SKU+参数+图片一次提交，发布后为草稿） */
    @PostMapping("/product")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Long> addProduct(@RequestBody ProductPublishRequest request) {
        return Result.success(merchantService.addProduct(request));
    }

    /** 编辑商品 */
    @PutMapping("/product/{id}")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> updateProduct(@PathVariable Long id, @RequestBody ProductPublishRequest request) {
        merchantService.updateProduct(id, request);
        return Result.success();
    }

    /** 商品上架/下架 */
    @PutMapping("/product/{id}/status")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> updateProductStatus(@PathVariable Long id, @RequestParam Integer status) {
        merchantService.updateProductStatus(id, status);
        return Result.success();
    }

    /** 本店订单列表 */
    @GetMapping("/order/page")
    @RequireRole(RequireRole.MERCHANT)
    public Result<PageResult<MerchantOrderVO>> orderPage(@RequestParam(required = false) Integer status,
                                                         @RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(merchantService.orderPage(status, pageNum, pageSize));
    }

    /** 发货（待发货→待收货） */
    @PostMapping("/order/{id}/deliver")
    @RequireRole(RequireRole.MERCHANT)
    public Result<Void> deliver(@PathVariable Long id, @RequestParam String logisticsNo,
                                @RequestParam String companyName) {
        merchantService.deliver(id, logisticsNo, companyName);
        return Result.success();
    }

    /** 经营数据统计 */
    @GetMapping("/stats")
    @RequireRole(RequireRole.MERCHANT)
    public Result<MerchantStatsVO> stats() {
        return Result.success(merchantService.stats());
    }
}
