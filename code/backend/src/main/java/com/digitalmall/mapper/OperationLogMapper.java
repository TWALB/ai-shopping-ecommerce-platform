package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.OperationLog;
import com.digitalmall.vo.OperationLogVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 操作日志 Mapper
 */
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /** 操作日志分页（按模块/操作人筛选） */
    @Select("<script>" +
            "SELECT l.id, l.module, l.action, l.ip, l.created_time, IFNULL(u.nickname, u.username) AS username " +
            "FROM operation_log l LEFT JOIN sys_user u ON l.user_id = u.id " +
            "<where>" +
            "<if test='module != null and module != \"\"'> AND l.module = #{module} </if>" +
            "<if test='username != null and username != \"\"'> AND (u.nickname LIKE CONCAT('%', #{username}, '%') " +
            "OR u.username LIKE CONCAT('%', #{username}, '%')) </if>" +
            "</where>" +
            "ORDER BY l.id DESC" +
            "</script>")
    IPage<OperationLogVO> selectLogPage(Page<OperationLogVO> page,
                                        @Param("module") String module,
                                        @Param("username") String username);
}
