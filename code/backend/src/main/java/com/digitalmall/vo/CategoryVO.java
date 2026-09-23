package com.digitalmall.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品分类树节点
 */
@Data
public class CategoryVO {

    private Long id;

    private String categoryName;

    private String icon;

    private List<CategoryVO> children;
}
