package com.digitalmall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 商品参数属性表（键值对：屏幕/处理器/电池等）
 */
@Data
@TableName("product_attr")
public class ProductAttr {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private String attrName;

    private String attrValue;

    private Integer sortOrder;
}
