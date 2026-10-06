package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.MerchantApply;
import com.digitalmall.vo.MerchantApplyAdminVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商家入驻申请 Mapper
 */
public interface MerchantApplyMapper extends BaseMapper<MerchantApply> {

    /** 入驻申请审核分页（含申请人账号昵称，按状态筛选） */
    @Select("<script>" +
            "SELECT a.id, a.real_name, a.id_card, a.contact_phone, a.shop_name, a.shop_intro, " +
            "a.qualification_url, a.status, a.reject_reason, a.created_time, u.username, u.nickname " +
            "FROM merchant_apply a JOIN sys_user u ON a.user_id = u.id " +
            "<where>" +
            "<if test='status != null'> AND a.status = #{status} </if>" +
            "</where>" +
            "ORDER BY a.created_time DESC" +
            "</script>")
    IPage<MerchantApplyAdminVO> selectApplyPage(Page<MerchantApplyAdminVO> page, @Param("status") Integer status);
}
