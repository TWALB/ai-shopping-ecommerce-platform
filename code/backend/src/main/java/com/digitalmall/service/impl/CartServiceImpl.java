package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.common.BusinessException;
import com.digitalmall.entity.Cart;
import com.digitalmall.entity.ProductSku;
import com.digitalmall.mapper.CartMapper;
import com.digitalmall.mapper.ProductSkuMapper;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.CartService;
import com.digitalmall.vo.CartVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车服务实现
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductSkuMapper productSkuMapper;

    private Long uid() {
        return UserContext.getUserId();
    }

    @Override
    public List<CartVO> list() {
        return cartMapper.selectCartVOList(uid());
    }

    @Override
    public void add(Long skuId, Integer quantity) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BusinessException(404, "商品规格不存在或已下架");
        }
        Cart exist = cartMapper.selectOne(Wrappers.<Cart>lambdaQuery()
                .eq(Cart::getUserId, uid())
                .eq(Cart::getSkuId, skuId));
        if (exist != null) {
            int newQty = exist.getQuantity() + quantity;
            if (newQty > sku.getStock()) {
                throw new BusinessException("库存不足");
            }
            exist.setQuantity(newQty);
            cartMapper.updateById(exist);
        } else {
            if (quantity > sku.getStock()) {
                throw new BusinessException("库存不足");
            }
            Cart cart = new Cart();
            cart.setUserId(uid());
            cart.setSkuId(skuId);
            cart.setQuantity(quantity);
            cart.setIsSelected(1);
            cartMapper.insert(cart);
        }
    }

    @Override
    public void updateQuantity(Long id, Integer quantity) {
        Cart cart = checkOwn(id);
        ProductSku sku = productSkuMapper.selectById(cart.getSkuId());
        if (sku != null && quantity > sku.getStock()) {
            throw new BusinessException("库存不足");
        }
        cart.setQuantity(quantity);
        cartMapper.updateById(cart);
    }

    @Override
    public void updateSelected(Long id, Boolean isSelected) {
        Cart cart = checkOwn(id);
        cart.setIsSelected(Boolean.TRUE.equals(isSelected) ? 1 : 0);
        cartMapper.updateById(cart);
    }

    @Override
    public void delete(Long id) {
        checkOwn(id);
        cartMapper.deleteById(id);
    }

    @Override
    public void clearSelected() {
        cartMapper.delete(Wrappers.<Cart>lambdaQuery()
                .eq(Cart::getUserId, uid())
                .eq(Cart::getIsSelected, 1));
    }

    /** 归属校验 */
    private Cart checkOwn(Long id) {
        Cart cart = cartMapper.selectById(id);
        if (cart == null) {
            throw new BusinessException(404, "购物车项不存在");
        }
        if (!cart.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权操作该购物车项");
        }
        return cart;
    }
}
