package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import org.springframework.web.bind.annotation.*;

/**
 * 商品模块（接口文档：三、商品模块 浏览/检索）— 公开接口
 * 开发阶段按接口文档逐个实现
 */
@RestController
@RequestMapping("/api/product")
public class ProductController {

    /** 商品分页检索（关键词/分类/品牌/价格/排序） */
    @GetMapping("/page")
    public Result<PageResult<?>> page(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(required = false) Long brandId,
                                      @RequestParam(required = false) java.math.BigDecimal minPrice,
                                      @RequestParam(required = false) java.math.BigDecimal maxPrice,
                                      @RequestParam(required = false, defaultValue = "comprehensive") String sortBy,
                                      @RequestParam(required = false, defaultValue = "desc") String order,
                                      @RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "10") long pageSize) {
        // TODO
        return Result.success(null);
    }

    /** 商品详情（SKU/参数/图片/店铺/评价摘要） */
    @GetMapping("/{id}")
    public Result<Void> detail(@PathVariable Long id) {
        // TODO 访问时自增 view_count
        return Result.success();
    }

    /** 分类树 */
    @GetMapping("/category/tree")
    public Result<Void> categoryTree() {
        // TODO
        return Result.success();
    }

    /** 品牌列表 */
    @GetMapping("/brand/list")
    public Result<Void> brandList() {
        // TODO
        return Result.success();
    }

    /** 热门商品 Top10（Redis ZSet） */
    @GetMapping("/hot")
    public Result<Void> hot() {
        // TODO
        return Result.success();
    }
}
