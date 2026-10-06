package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.SysUser;
import com.digitalmall.vo.UserAdminVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 用户管理分页（关键词/角色/状态筛选，含店铺名） */
    @Select("<script>" +
            "SELECT u.id, u.username, u.nickname, u.phone, u.role, u.status, u.created_time, " +
            "(SELECT s.shop_name FROM shop s WHERE s.merchant_id = u.id) AS shop_name " +
            "FROM sys_user u " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'> AND (u.username LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.nickname LIKE CONCAT('%', #{keyword}, '%') OR u.phone LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='role != null'> AND u.role = #{role} </if>" +
            "<if test='status != null'> AND u.status = #{status} </if>" +
            "</where>" +
            "ORDER BY u.id DESC" +
            "</script>")
    IPage<UserAdminVO> selectAdminUserPage(Page<UserAdminVO> page,
                                           @Param("keyword") String keyword,
                                           @Param("role") Integer role,
                                           @Param("status") Integer status);
}
