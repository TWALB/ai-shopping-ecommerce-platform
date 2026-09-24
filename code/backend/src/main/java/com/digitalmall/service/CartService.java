package com.digitalmall.service;

import com.digitalmall.vo.CartVO;

import java.util.List;

/**
 * 购物车服务
 */
public interface CartService {

    /** 购物车列表（含商品与SKU信息） */
    List<CartVO> list();

    /** 加入购物车（已存在则累加，超库存 409） */
    void add(Long skuId, Integer quantity);

    /** 修改数量 */
    void updateQuantity(Long id, Integer quantity);

    /** 勾选/取消勾选 */
    void updateSelected(Long id, Boolean isSelected);

    /** 删除购物车项 */
    void delete(Long id);

    /** 下单后清空勾选项 */
    void clearSelected();
}
