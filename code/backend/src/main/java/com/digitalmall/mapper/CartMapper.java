package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.digitalmall.entity.Cart;
import com.digitalmall.vo.CartVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CartMapper extends BaseMapper<Cart> {

    /** 购物车列表：联表商品/SKU 出展示 VO */
    @Select("SELECT c.id, c.sku_id, p.id AS product_id, p.product_name, p.main_image, s.sku_name, s.price, " +
            "c.quantity, c.is_selected, s.stock, p.status, p.shop_id " +
            "FROM cart c " +
            "JOIN product_sku s ON c.sku_id = s.id " +
            "JOIN product p ON s.product_id = p.id " +
            "WHERE c.user_id = #{userId} " +
            "ORDER BY c.updated_time DESC")
    List<CartVO> selectCartVOList(@Param("userId") Long userId);

    /** 购物车勾选项（结算用） */
    @Select("SELECT c.id, c.sku_id, p.id AS product_id, p.product_name, p.main_image, s.sku_name, s.price, " +
            "c.quantity, c.is_selected, s.stock, p.status, p.shop_id " +
            "FROM cart c " +
            "JOIN product_sku s ON c.sku_id = s.id " +
            "JOIN product p ON s.product_id = p.id " +
            "WHERE c.user_id = #{userId} AND c.is_selected = 1 " +
            "ORDER BY c.updated_time DESC")
    List<CartVO> selectSelectedVOList(@Param("userId") Long userId);
}
