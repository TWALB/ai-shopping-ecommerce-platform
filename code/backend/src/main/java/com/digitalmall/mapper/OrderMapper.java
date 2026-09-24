package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.Order;
import com.digitalmall.vo.OrderVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OrderMapper extends BaseMapper<Order> {

    /** 我的订单分页（含店铺名/件数/首图），支持状态筛选 */
    @Select("<script>" +
            "SELECT o.id, o.order_no, o.shop_id, s.shop_name, o.status, o.total_amount, o.freight_amount, " +
            "o.pay_amount, o.created_time, " +
            "(SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count, " +
            "(SELECT MIN(oi2.product_image) FROM order_item oi2 WHERE oi2.order_id = o.id) AS first_image " +
            "FROM `order` o JOIN shop s ON o.shop_id = s.id " +
            "WHERE o.user_id = #{userId} " +
            "<if test='status != null'> AND o.status = #{status} </if>" +
            "ORDER BY o.created_time DESC" +
            "</script>")
    IPage<OrderVO> selectOrderVOList(Page<OrderVO> page, @Param("userId") Long userId, @Param("status") Integer status);
}
