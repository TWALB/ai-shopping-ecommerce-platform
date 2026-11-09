package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.Complaint;
import com.digitalmall.vo.ComplaintVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 咨询/投诉 Mapper
 */
public interface ComplaintMapper extends BaseMapper<Complaint> {

    /** 用户端：我提交的咨询/投诉分页 */
    @Select("<script>" +
            "SELECT c.id, c.user_id, c.shop_id, c.order_id, o.order_no, " +
            "IFNULL(NULLIF(u.nickname, ''), IFNULL(NULLIF(u.username, ''), '用户')) AS username, " +
            "s.shop_name, c.type, c.content, c.status, c.reply_content, c.reply_time, c.created_time " +
            "FROM complaint c " +
            "LEFT JOIN `order` o ON c.order_id = o.id " +
            "LEFT JOIN sys_user u ON c.user_id = u.id " +
            "LEFT JOIN shop s ON c.shop_id = s.id " +
            "<where> c.user_id = #{userId} " +
            "<if test='status != null'> AND c.status = #{status} </if>" +
            "</where>" +
            " ORDER BY c.created_time DESC" +
            "</script>")
    IPage<ComplaintVO> selectUserPage(Page<ComplaintVO> page,
                                      @Param("userId") Long userId,
                                      @Param("status") Integer status);

    /** 商家端：本店收到的咨询/投诉分页 */
    @Select("<script>" +
            "SELECT c.id, c.user_id, c.shop_id, c.order_id, o.order_no, " +
            "IFNULL(NULLIF(u.nickname, ''), IFNULL(NULLIF(u.username, ''), '用户')) AS username, " +
            "s.shop_name, c.type, c.content, c.status, c.reply_content, c.reply_time, c.created_time " +
            "FROM complaint c " +
            "LEFT JOIN `order` o ON c.order_id = o.id " +
            "LEFT JOIN sys_user u ON c.user_id = u.id " +
            "LEFT JOIN shop s ON c.shop_id = s.id " +
            "<where> c.shop_id = #{shopId} " +
            "<if test='status != null'> AND c.status = #{status} </if>" +
            "</where>" +
            " ORDER BY c.created_time DESC" +
            "</script>")
    IPage<ComplaintVO> selectMerchantPage(Page<ComplaintVO> page,
                                          @Param("shopId") Long shopId,
                                          @Param("status") Integer status);
}
