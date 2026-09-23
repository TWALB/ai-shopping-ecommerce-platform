package com.digitalmall.service;

import com.digitalmall.common.PageResult;
import com.digitalmall.dto.ProductQuery;
import com.digitalmall.entity.Brand;
import com.digitalmall.vo.CategoryVO;
import com.digitalmall.vo.ProductDetailVO;
import com.digitalmall.vo.ProductVO;

import java.util.List;

/**
 * 商品服务：浏览 / 检索 / 详情 / 分类 / 热门
 */
public interface ProductService {

    /** 商品分页检索（关键词/分类含子分类/品牌/价格区间/排序） */
    PageResult<ProductVO> pageQuery(ProductQuery query);

    /** 商品详情（SKU/参数/图片/店铺/评价摘要），访问自增浏览量 */
    ProductDetailVO detail(Long id);

    /** 商品分类树 */
    List<CategoryVO> categoryTree();

    /** 品牌列表 */
    List<Brand> brandList();

    /** 热门商品 Top10（Redis ZSet 缓存，回源销量） */
    List<ProductVO> hot();
}
