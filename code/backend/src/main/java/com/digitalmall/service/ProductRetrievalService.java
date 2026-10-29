package com.digitalmall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.entity.Brand;
import com.digitalmall.entity.Category;
import com.digitalmall.entity.Product;
import com.digitalmall.entity.ProductAttr;
import com.digitalmall.mapper.BrandMapper;
import com.digitalmall.mapper.CategoryMapper;
import com.digitalmall.mapper.ProductAttrMapper;
import com.digitalmall.mapper.ProductMapper;
import com.digitalmall.vo.ProductBriefVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品知识库检索：
 * - 配置了 LLM 时：Embedding + Redis 余弦相似度召回
 * - 未配置/调用失败时：本地结构化匹配（品类/预算/关键词/品牌/销量）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRetrievalService {

    private static final String EMBED_KEY_PREFIX = "product:embedding:";
    private static final int TOP_N = 5;

    /** 关键词同义词组，提升"拍照/续航"等需求的命中率 */
    private static final Map<String, List<String>> SYNONYMS = Map.of(
            "拍照", List.of("拍照", "影像", "徕卡", "相机", "像素", "摄像头"),
            "续航", List.of("续航", "电池", "快充", "充电"),
            "游戏", List.of("游戏", "性能", "散热", "骁龙", "天玑", "高刷"),
            "轻薄", List.of("轻薄", "便携", "重量", "手感"),
            "降噪", List.of("降噪", "主动降噪", "噪音")
    );

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final ProductAttrMapper productAttrMapper;
    private final StringRedisTemplate redisTemplate;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 按槽位召回商品（Top5） */
    public List<ProductBriefVO> retrieve(Map<String, Object> slots, String content, String intent) {
        List<Product> products = onSaleProducts();
        if (products.isEmpty()) {
            return Collections.emptyList();
        }
        // 0. 对比意图：优先精确匹配用户点名的机型
        if ("compare".equals(intent) && content != null && !content.isBlank()) {
            List<Product> named = matchNamedProducts(products, content);
            if (!named.isEmpty()) {
                return named.stream()
                        .limit(TOP_N)
                        .map(p -> toBrief(p, 1.0))
                        .collect(Collectors.toList());
            }
        }
        // 1. 优先向量检索
        if (llmClient.available()) {
            try {
                List<ProductBriefVO> byVector = vectorRetrieve(products, slots);
                if (!byVector.isEmpty()) {
                    return byVector;
                }
            } catch (Exception e) {
                log.warn("向量召回失败，降级本地匹配: {}", e.getMessage());
            }
        }
        // 2. 本地结构化匹配
        return localRetrieve(products, slots);
    }

    /** 在用户输入中匹配被点名的商品（先去空格小写，兼容"小米15"与"小米 15"） */
    private List<Product> matchNamedProducts(List<Product> products, String content) {
        String norm = normalizeText(content);
        List<Product> named = products.stream()
                .filter(p -> p.getProductName() != null
                        && norm.contains(normalizeText(p.getProductName())))
                .collect(Collectors.toList());
        if (!named.isEmpty()) {
            return named;
        }
        // 退回品牌级匹配："小米和华为对比"
        List<Brand> brands = brandMapper.selectList(null);
        List<Product> byBrand = products.stream()
                .filter(p -> {
                    Brand b = brands.stream().filter(x -> x.getId().equals(p.getBrandId())).findFirst().orElse(null);
                    return b != null && b.getBrandName() != null && norm.contains(normalizeText(b.getBrandName()));
                })
                .collect(Collectors.toList());
        return byBrand;
    }

    private String normalizeText(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    /** 商品知识库向量化（full=true 全量重建） */
    public int syncKnowledge(boolean full) {
        if (!llmClient.available()) {
            throw new IllegalStateException("未配置可用的大模型 api-key，无法执行向量化；当前导购使用本地检索模式");
        }
        List<Product> products = onSaleProducts();
        if (full) {
            Set<String> keys = redisTemplate.keys(EMBED_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        int synced = 0;
        for (Product p : products) {
            try {
                float[] vec = llmClient.embed(buildProductText(p));
                if (vec == null) {
                    continue;
                }
                redisTemplate.opsForValue().set(
                        EMBED_KEY_PREFIX + p.getId(),
                        objectMapper.writeValueAsString(vec),
                        7, TimeUnit.DAYS);
                synced++;
            } catch (Exception e) {
                log.warn("商品 {} 向量化失败: {}", p.getId(), e.getMessage());
            }
        }
        return synced;
    }

    // ==================== 向量检索 ====================

    private List<ProductBriefVO> vectorRetrieve(List<Product> products, Map<String, Object> slots) {
        float[] queryVec = llmClient.embed(buildQuery(slots));
        if (queryVec == null) {
            return Collections.emptyList();
        }
        Map<Long, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        List<double[]> scored = new ArrayList<>();
        Set<String> keys = redisTemplate.keys(EMBED_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        for (String key : keys) {
            try {
                Long pid = Long.valueOf(key.substring(EMBED_KEY_PREFIX.length()));
                Product p = byId.get(pid);
                if (p == null) {
                    continue;
                }
                String raw = redisTemplate.opsForValue().get(key);
                if (raw == null) {
                    continue;
                }
                float[] vec = objectMapper.readValue(raw, float[].class);
                double cos = cosine(queryVec, vec);
                scored.add(new double[]{pid, cos});
            } catch (Exception e) {
                log.debug("读取向量失败 {}: {}", key, e.getMessage());
            }
        }
        return scored.stream()
                .sorted((a, b) -> Double.compare(b[1], a[1]))
                .limit(TOP_N)
                .map(s -> toBrief(byId.get((long) s[0]), s[1]))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String buildQuery(Map<String, Object> slots) {
        StringBuilder sb = new StringBuilder();
        if (slots != null) {
            sb.append(slots.getOrDefault("category", "")).append(' ')
              .append(slots.getOrDefault("keyword", "")).append(' ')
              .append(slots.getOrDefault("brand", "")).append(' ');
            Object budget = slots.get("budget");
            if (budget instanceof Number n && n.doubleValue() > 0) {
                sb.append("预算").append(n.intValue()).append("元 ");
            }
        }
        return sb.toString().trim();
    }

    private String buildProductText(Product p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getProductName()).append(' ');
        if (p.getProductTitle() != null) sb.append(p.getProductTitle()).append(' ');
        if (p.getTags() != null) sb.append(p.getTags()).append(' ');
        if (p.getKeywords() != null) sb.append(p.getKeywords()).append(' ');
        if (p.getBrief() != null) sb.append(p.getBrief()).append(' ');
        sb.append("价格").append(p.getPrice()).append("元 ");
        List<ProductAttr> attrs = productAttrMapper.selectList(Wrappers.<ProductAttr>lambdaQuery()
                .eq(ProductAttr::getProductId, p.getId())
                .orderByAsc(ProductAttr::getSortOrder));
        for (ProductAttr a : attrs) {
            sb.append(a.getAttrName()).append(':').append(a.getAttrValue()).append(' ');
        }
        return sb.toString().trim();
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ==================== 本地匹配 ====================

    private List<ProductBriefVO> localRetrieve(List<Product> products, Map<String, Object> slots) {
        Map<Long, String> categoryNames = categoryNameMap();
        Map<Long, String> brandNames = brandNameMap();
        String category = str(slots, "category");
        String keyword = str(slots, "keyword");
        String brand = str(slots, "brand");
        double budget = slots != null && slots.get("budget") instanceof Number n ? n.doubleValue() : 0;

        // 品类筛选：问"手机"不应召回耳机/平板（命中不足 3 件时才放开）
        List<Product> pool = products;
        if (!category.isEmpty()) {
            List<Product> matched = products.stream()
                    .filter(p -> categoryNames.getOrDefault(p.getCategoryId(), "").contains(category)
                            || productText(p).contains(category))
                    .collect(Collectors.toList());
            if (matched.size() >= 3) {
                pool = matched;
            }
        }
        // 预算上限：超预算 20% 以上剔除（留少量上浮空间）
        if (budget > 0) {
            List<Product> inBudget = pool.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice().doubleValue() <= budget * 1.2)
                    .collect(Collectors.toList());
            if (inBudget.size() >= 2) {
                pool = inBudget;
            }
        }

        List<String> kwGroup = expandKeyword(keyword);
        List<double[]> scored = new ArrayList<>();
        for (Product p : pool) {
            double score = 0.5; // 基础分
            String text = productText(p);
            String catName = categoryNames.getOrDefault(p.getCategoryId(), "");

            if (!category.isEmpty() && (catName.contains(category) || text.contains(category))) {
                score += 3;
            }
            if (!brand.isEmpty()) {
                String bName = brandNames.getOrDefault(p.getBrandId(), "");
                if (bName.contains(brand) || text.contains(brand)) {
                    score += 2;
                }
            }
            if (!kwGroup.isEmpty()) {
                long hits = kwGroup.stream().filter(text::contains).count();
                if (hits > 0) {
                    score += 2 * Math.min(hits, 2);
                }
            }
            if (budget > 0) {
                double price = p.getPrice() == null ? 0 : p.getPrice().doubleValue();
                score += price <= budget ? 2 : -1;
            }
            if (p.getSales() != null) {
                // 销量仅作微调，避免低价爆款压过用户真实需求
                score += Math.min(p.getSales() / 5000.0, 0.5);
            }
            scored.add(new double[]{p.getId(), score});
        }
        Map<Long, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        double max = scored.stream().mapToDouble(s -> s[1]).max().orElse(1);
        return scored.stream()
                .sorted((a, b) -> Double.compare(b[1], a[1]))
                .limit(TOP_N)
                .map(s -> toBrief(byId.get((long) s[0]), max <= 0 ? 0.5 : Math.min(s[1] / max, 1.0)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 商品可检索文本（名称/标题/标签/关键词/简介） */
    private String productText(Product p) {
        return (p.getProductName() == null ? "" : p.getProductName())
                + " " + (p.getProductTitle() == null ? "" : p.getProductTitle())
                + " " + (p.getTags() == null ? "" : p.getTags())
                + " " + (p.getKeywords() == null ? "" : p.getKeywords())
                + " " + (p.getBrief() == null ? "" : p.getBrief());
    }

    private List<String> expandKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return SYNONYMS.getOrDefault(keyword, List.of(keyword));
    }

    private Map<Long, String> categoryNameMap() {
        List<Category> cats = categoryMapper.selectList(null);
        return cats.stream().filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Category::getId, c -> c.getCategoryName() == null ? "" : c.getCategoryName()));
    }

    private Map<Long, String> brandNameMap() {
        List<Brand> brands = brandMapper.selectList(null);
        return brands.stream().filter(b -> b.getId() != null)
                .collect(Collectors.toMap(Brand::getId, b -> b.getBrandName() == null ? "" : b.getBrandName()));
    }

    private List<Product> onSaleProducts() {
        return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSales));
    }

    private ProductBriefVO toBrief(Product p, double similarity) {
        if (p == null) {
            return null;
        }
        ProductBriefVO vo = new ProductBriefVO();
        vo.setId(p.getId());
        vo.setProductName(p.getProductName());
        vo.setPrice(p.getPrice());
        vo.setMainImage(p.getMainImage());
        vo.setSales(p.getSales());
        vo.setSimilarity(Math.round(similarity * 100) / 100.0);
        return vo;
    }

    private String str(Map<String, Object> slots, String key) {
        if (slots == null || slots.get(key) == null) {
            return "";
        }
        return String.valueOf(slots.get(key)).trim();
    }
}
