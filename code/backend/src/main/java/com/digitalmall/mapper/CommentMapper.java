package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.Comment;
import com.digitalmall.vo.CommentVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商品评价 Mapper
 */
public interface CommentMapper extends BaseMapper<Comment> {

    /** 商品已展示评价分页（公开，仅 status=1） */
    @Select("SELECT c.id, c.product_score, c.logistics_score, c.content, c.image_urls, c.is_anonymous, " +
            "c.reply_content, c.reply_time, c.created_time, " +
            "CASE WHEN c.is_anonymous = 1 THEN '匿名用户' ELSE IFNULL(u.nickname, '用户') END AS nickname " +
            "FROM `comment` c LEFT JOIN sys_user u ON c.user_id = u.id " +
            "WHERE c.product_id = #{productId} AND c.status = 1 " +
            "ORDER BY c.created_time DESC")
    IPage<CommentVO> selectProductComments(Page<CommentVO> page, @Param("productId") Long productId);

    /** 评价管理分页（商家限定本店 / 管理员全部，按状态筛选、商品名模糊） */
    @Select("<script>" +
            "SELECT c.id, c.product_score, c.logistics_score, c.content, c.is_anonymous, c.status, " +
            "c.reply_content, c.reply_time, c.created_time, p.product_name, s.shop_name, " +
            "CASE WHEN c.is_anonymous = 1 THEN '匿名用户' ELSE IFNULL(u.nickname, '用户') END AS nickname " +
            "FROM `comment` c " +
            "JOIN product p ON c.product_id = p.id " +
            "JOIN shop s ON p.shop_id = s.id " +
            "LEFT JOIN sys_user u ON c.user_id = u.id " +
            "<where>" +
            "<if test='shopId != null'> AND p.shop_id = #{shopId} </if>" +
            "<if test='status != null'> AND c.status = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'> AND p.product_name LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "</where>" +
            "ORDER BY c.created_time DESC" +
            "</script>")
    IPage<CommentVO> selectManageComments(Page<CommentVO> page,
                                          @Param("shopId") Long shopId,
                                          @Param("status") Integer status,
                                          @Param("keyword") String keyword);
}
