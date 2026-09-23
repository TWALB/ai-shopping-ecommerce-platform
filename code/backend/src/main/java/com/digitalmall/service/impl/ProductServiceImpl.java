package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.common.BusinessException;
import com.digitalmall.common.PageResult;
import com.digitalmall.dto.ProductQuery;
import com.digitalmall.entity.*;
import com.digitalmall.mapper.*;
import com.digitalmall.service.ProductService;
import com.digitalmall.vo.CategoryVO;
import com.digitalmall.vo.CommentSummaryVO;
import com.digitalmall.vo.ProductDetailVO;
import com.digitalmall.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品服务实现
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductAttrMapper productAttrMapper;
    private final ProductImageMapper productImageMapper;
    private final ShopMapper shopMapper;
    private final CommentMapper commentMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 热门商品缓存 key（ZSet：member=productId, score=sales） */
    private static final String KEY_HOT = "product:hot";

    @Override
    public PageResult<ProductVO> pageQuery(ProductQuery query) {
        LambdaQueryWrapper<Product> qw = Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, 1); // 仅上架

        // 关键词：商品名 / keywords / tags 模糊匹配
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and(w -> w.like(Product::getProductName, kw)
                    .or().like(Product::getKeywords, kw)
                    .or().like(Product::getTags, kw));
        }
        // 分类（含子分类）
        List<Long> categoryIds = collectCategoryIds(query.getCategoryId());
        if (!categoryIds.isEmpty()) {
            qw.in(Product::getCategoryId, categoryIds);
        }
        // 品牌
        if (query.getBrandId() != null) {
            qw.eq(Product::getBrandId, query.getBrandId());
        }
        // 价格区间
        if (query.getMinPrice() != null) {
            qw.ge(Product::getPrice, query.getMinPrice());
        }
        if (query.getMaxPrice() != null) {
            qw.le(Product::getPrice, query.getMaxPrice());
        }
        // 排序
        applySort(qw, query);

        Page<Product> page = new Page<>(query.getPageNum(), query.getPageSize());
        productMapper.selectPage(page, qw);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), toVOList(page.getRecords()));
    }

    @Override
    public ProductDetailVO detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        // 浏览量自增
        productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                .eq(Product::getId, id)
                .setSql("view_count = view_count + 1"));

        ProductDetailVO vo = new ProductDetailVO();
        vo.setProduct(product);
        vo.setSkus(productSkuMapper.selectList(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getProductId, id)
                .eq(ProductSku::getStatus, 1)
                .orderByAsc(ProductSku::getId)));
        vo.setAttrs(productAttrMapper.selectList(Wrappers.<ProductAttr>lambdaQuery()
                .eq(ProductAttr::getProductId, id)
                .orderByAsc(ProductAttr::getSortOrder)));
        vo.setImages(productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id)
                .orderByAsc(ProductImage::getSortOrder)));
        vo.setShop(shopMapper.selectById(product.getShopId()));

        // 评价摘要（已展示评价）
        List<Comment> comments = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getProductId, id)
                .eq(Comment::getStatus, 1));
        CommentSummaryVO summary = new CommentSummaryVO();
        summary.setTotal((long) comments.size());
        summary.setAvgProductScore(comments.stream()
                .mapToInt(c -> c.getProductScore() == null ? 0 : c.getProductScore())
                .average().orElse(0));
        summary.setAvgLogisticsScore(comments.stream()
                .mapToInt(c -> c.getLogisticsScore() == null ? 0 : c.getLogisticsScore())
                .average().orElse(0));
        vo.setCommentSummary(summary);
        return vo;
    }

    @Override
    public List<CategoryVO> categoryTree() {
        List<Category> all = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSortOrder));
        Map<Long, CategoryVO> voMap = new LinkedHashMap<>();
        for (Category c : all) {
            CategoryVO v = new CategoryVO();
            v.setId(c.getId());
            v.setCategoryName(c.getCategoryName());
            v.setIcon(c.getIcon());
            v.setChildren(new ArrayList<>());
            voMap.put(c.getId(), v);
        }
        List<CategoryVO> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryVO v = voMap.get(c.getId());
            if (c.getParentId() == null || c.getParentId() == 0) {
                roots.add(v);
            } else {
                CategoryVO parent = voMap.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(v);
                } else {
                    roots.add(v);
                }
            }
        }
        return roots;
    }

    @Override
    public List<Brand> brandList() {
        return brandMapper.selectList(Wrappers.<Brand>lambdaQuery()
                .eq(Brand::getStatus, 1)
                .orderByAsc(Brand::getId));
    }

    @Override
    public List<ProductVO> hot() {
        ZSetOperations<String, String> zset = stringRedisTemplate.opsForZSet();
        Set<String> cachedIds = zset.reverseRange(KEY_HOT, 0, 9);
        List<Long> productIds;
        if (cachedIds == null || cachedIds.isEmpty()) {
            // 缓存未命中：回源数据库销量 Top10 并回填
            List<Product> top = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                    .eq(Product::getStatus, 1)
                    .orderByDesc(Product::getSales)
                    .last("LIMIT 10"));
            productIds = top.stream().map(Product::getId).toList();
            for (Product p : top) {
                zset.add(KEY_HOT, String.valueOf(p.getId()),
                        p.getSales() == null ? 0 : p.getSales().doubleValue());
            }
        } else {
            productIds = cachedIds.stream().map(Long::valueOf).toList();
        }
        if (productIds.isEmpty()) {
            return Collections.emptyList();
        }
        return toVOList(productMapper.selectBatchIds(productIds));
    }

    // ==================== 私有辅助 ====================

    /** 收集分类ID及其全部后代（BFS） */
    private List<Long> collectCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptyList();
        }
        List<Category> all = categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery().eq(Category::getStatus, 1));
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        boolean added;
        do {
            added = false;
            for (Category c : all) {
                if (!ids.contains(c.getId()) && ids.contains(c.getParentId())) {
                    ids.add(c.getId());
                    added = true;
                }
            }
        } while (added);
        return ids;
    }

    /** 应用排序规则 */
    private void applySort(LambdaQueryWrapper<Product> qw, ProductQuery query) {
        String sortBy = query.getSortBy() == null ? "comprehensive" : query.getSortBy();
        boolean asc = "asc".equalsIgnoreCase(query.getOrder());
        switch (sortBy) {
            case "price" -> {
                if (asc) qw.orderByAsc(Product::getPrice);
                else qw.orderByDesc(Product::getPrice);
            }
            case "sales" -> qw.orderByDesc(Product::getSales);
            case "view_count" -> qw.orderByDesc(Product::getViewCount);
            case "recommend" -> qw.orderByDesc(Product::getIsRecommend).orderByDesc(Product::getSales);
            default -> qw.orderByDesc(Product::getSales).orderByAsc(Product::getId);
        }
    }

    /** 商品实体 → 列表VO（补充店铺名） */
    private List<ProductVO> toVOList(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> shopIds = products.stream().map(Product::getShopId).collect(Collectors.toSet());
        Map<Long, String> shopNames = shopMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Shop::getShopName, (a, b) -> a));
        return products.stream().map(p -> {
            ProductVO vo = new ProductVO();
            BeanUtils.copyProperties(p, vo);
            vo.setShopName(shopNames.get(p.getShopId()));
            return vo;
        }).toList();
    }
}
