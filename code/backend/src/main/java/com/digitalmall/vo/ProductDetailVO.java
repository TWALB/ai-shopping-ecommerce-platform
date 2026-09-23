package com.digitalmall.vo;

import com.digitalmall.entity.Product;
import com.digitalmall.entity.ProductAttr;
import com.digitalmall.entity.ProductImage;
import com.digitalmall.entity.ProductSku;
import com.digitalmall.entity.Shop;
import lombok.Data;

import java.util.List;

/**
 * 商品详情（商品 + SKU + 参数 + 图片 + 店铺 + 评价摘要）
 */
@Data
public class ProductDetailVO {

    private Product product;

    private List<ProductSku> skus;

    private List<ProductAttr> attrs;

    private List<ProductImage> images;

    private Shop shop;

    private CommentSummaryVO commentSummary;
}
