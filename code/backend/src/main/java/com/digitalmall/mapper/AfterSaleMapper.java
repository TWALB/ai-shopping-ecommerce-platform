package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.AfterSale;
import com.digitalmall.vo.AfterSaleAdminVO;
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
}
