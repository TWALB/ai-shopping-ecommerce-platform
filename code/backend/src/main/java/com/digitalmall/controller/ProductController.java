package com.digitalmall.controller;

import com.digitalmall.common.PageResult;
import com.digitalmall.common.Result;
import com.digitalmall.dto.ProductQuery;
import com.digitalmall.entity.Brand;
import com.digitalmall.service.ProductService;
import com.digitalmall.vo.CategoryVO;
import com.digitalmall.vo.ProductDetailVO;
import com.digitalmall.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品模块（接口文档：三、商品模块 浏览/检索）— 公开接口
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 商品分页检索（关键词/分类/品牌/价格/排序） */
    @GetMapping("/page")
    public Result<PageResult<ProductVO>> page(ProductQuery query) {
        return Result.success(productService.pageQuery(query));
    }

    /** 商品详情（SKU/参数/图片/店铺/评价摘要），访问自增浏览量 */
    @GetMapping("/{id}")
    public Result<ProductDetailVO> detail(@PathVariable Long id) {
        return Result.success(productService.detail(id));
    }

    /** 分类树 */
    @GetMapping("/category/tree")
    public Result<List<CategoryVO>> categoryTree() {
        return Result.success(productService.categoryTree());
    }

    /** 品牌列表 */
    @GetMapping("/brand/list")
    public Result<List<Brand>> brandList() {
        return Result.success(productService.brandList());
    }

    /** 热门商品 Top10（Redis ZSet） */
    @GetMapping("/hot")
    public Result<List<ProductVO>> hot() {
        return Result.success(productService.hot());
    }
}
