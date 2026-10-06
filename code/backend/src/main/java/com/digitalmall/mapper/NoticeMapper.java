package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.Notice;
import com.digitalmall.vo.NoticeVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 公告 Mapper
 */
public interface NoticeMapper extends BaseMapper<Notice> {

    /** 公告分页（含发布人昵称，按状态筛选） */
    @Select("<script>" +
            "SELECT n.id, n.title, n.content, n.type, n.status, n.publish_time, n.created_time, " +
            "IFNULL(u.nickname, u.username) AS publisher_name " +
            "FROM notice n LEFT JOIN sys_user u ON n.publisher_id = u.id " +
            "<where>" +
            "<if test='status != null'> AND n.status = #{status} </if>" +
            "</where>" +
            "ORDER BY n.created_time DESC" +
            "</script>")
    IPage<NoticeVO> selectNoticePage(Page<NoticeVO> page, @Param("status") Integer status);
}
