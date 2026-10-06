package com.digitalmall.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商家发布/编辑商品（商品 + SKU + 参数 + 图片一次提交）
 */
@Data
public class ProductPublishRequest {

    private Long categoryId;

    private Long brandId;

    private String productName;

    private String productTitle;

    private String mainImage;

    private String keywords;

    private String tags;

    private String brief;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private List<SkuLine> skus;

    private List<AttrLine> attrs;

    private List<ImageLine> images;

    @Data
    public static class SkuLine {
        private String skuName;
        private BigDecimal price;
        private Integer stock;
        private String skuImage;
    }

    @Data
    public static class AttrLine {
        private String attrName;
        private String attrValue;
    }

    @Data
    public static class ImageLine {
        private String imageUrl;
        private Integer imageType;
    }
}
