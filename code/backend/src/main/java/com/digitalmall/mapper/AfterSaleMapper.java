package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.AfterSale;
import com.digitalmall.vo.AfterSaleAdminVO;
import com.digitalmall.vo.AfterSaleDetailVO;
import com.digitalmall.vo.AfterSaleItemVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 售后单 Mapper
 */
public interface AfterSaleMapper extends BaseMapper<AfterSale> {

    /** 售后单管理分页（含用户/商品信息，按状态筛选） */
    @Select("<script>" +
            "SELECT a.id, a.after_sale_no, a.order_id, a.type, a.reason, a.amount, a.status, " +
            "a.reject_reason, a.handle_time, a.created_time, " +
            "IFNULL(u.nickname, u.username) AS username, " +
            "IFNULL(oi.product_name, '订单商品') AS product_name " +
            "FROM after_sale a " +
            "LEFT JOIN sys_user u ON a.user_id = u.id " +
            "LEFT JOIN order_item oi ON a.order_item_id = oi.id " +
            "<where>" +
            "<if test='status != null'> AND a.status = #{status} </if>" +
            "</where>" +
            "ORDER BY a.created_time DESC" +
            "</script>")
    IPage<AfterSaleAdminVO> selectAfterSalePage(Page<AfterSaleAdminVO> page, @Param("status") Integer status);

    /** 用户端：我的售后单分页 */
    @Select("<script>" +
            "SELECT a.id, a.after_sale_no, a.order_id, o.order_no, a.order_item_id, a.type, a.reason, a.amount, " +
            "a.status, a.reject_reason, a.created_time, a.handle_time, " +
            "oi.product_name, oi.product_image, oi.sku_name, oi.quantity " +
            "FROM after_sale a " +
            "LEFT JOIN `order` o ON a.order_id = o.id " +
            "LEFT JOIN order_item oi ON a.order_item_id = oi.id " +
            "<where> a.user_id = #{userId} " +
            "<if test='status != null'> AND a.status = #{status} </if>" +
            "<if test='orderId != null'> AND a.order_id = #{orderId} </if>" +
            "</where>" +
            " ORDER BY a.created_time DESC" +
            "</script>")
    IPage<AfterSaleItemVO> selectUserPage(Page<AfterSaleItemVO> page,
                                          @Param("userId") Long userId,
                                          @Param("status") Integer status,
                                          @Param("orderId") Long orderId);

    /** 商家端：本店售后单分页 */
    @Select("<script>" +
            "SELECT a.id, a.after_sale_no, a.order_id, o.order_no, a.order_item_id, a.type, a.reason, a.amount, " +
            "a.status, a.reject_reason, a.created_time, a.handle_time, " +
            "oi.product_name, oi.product_image, oi.sku_name, oi.quantity " +
            "FROM after_sale a " +
            "LEFT JOIN `order` o ON a.order_id = o.id " +
            "LEFT JOIN order_item oi ON a.order_item_id = oi.id " +
            "<where> o.shop_id = #{shopId} " +
            "<if test='status != null'> AND a.status = #{status} </if>" +
            "</where>" +
            " ORDER BY a.created_time DESC" +
            "</script>")
    IPage<AfterSaleItemVO> selectMerchantPage(Page<AfterSaleItemVO> page,
                                              @Param("shopId") Long shopId,
                                              @Param("status") Integer status);

    /** 售后单详情（含商品、订单、店铺、收货快照） */
    @Select("SELECT a.id, a.after_sale_no, a.order_id, o.order_no, a.order_item_id, a.type, a.reason, a.amount, " +
            "a.status, a.reject_reason, a.created_time, a.handle_time, a.user_id, " +
            "IFNULL(u.nickname, u.username) AS username, " +
            "oi.product_name, oi.product_image, oi.sku_name, oi.quantity, " +
            "o.status AS order_status, o.pay_amount AS order_pay_amount, " +
            "o.receiver_name, o.receiver_phone, o.receiver_address, s.shop_name " +
            "FROM after_sale a " +
            "LEFT JOIN `order` o ON a.order_id = o.id " +
            "LEFT JOIN order_item oi ON a.order_item_id = oi.id " +
            "LEFT JOIN sys_user u ON a.user_id = u.id " +
            "LEFT JOIN shop s ON o.shop_id = s.id " +
            "WHERE a.id = #{id}")
    AfterSaleDetailVO selectDetail(@Param("id") Long id);
}
