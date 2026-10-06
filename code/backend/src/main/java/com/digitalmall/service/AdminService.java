package com.digitalmall.service;

import com.digitalmall.common.PageResult;
import com.digitalmall.dto.NoticeRequest;
import com.digitalmall.vo.*;

import java.util.List;

/**
 * 管理员服务：用户管控 / 入驻审核 / 商品订单管控 / 公告 / 统计 / 日志
 */
public interface AdminService {

    /** 用户管理分页 */
    PageResult<UserAdminVO> userPage(String keyword, Integer role, Integer status, long pageNum, long pageSize);

    /** 启用/禁用用户（禁用同时冻结店铺） */
    void userStatus(Long id, Integer status);

    /** 调整用户角色 */
    void userRole(Long id, Integer role);

    /** 入驻申请分页 */
    PageResult<MerchantApplyAdminVO> applyPage(Integer status, long pageNum, long pageSize);

    /** 审核入驻（通过自动开店铺 + 用户 role=1） */
    void auditApply(Long id, Boolean agree, String rejectReason);

    /** 平台商品分页 */
    PageResult<AdminProductVO> productPage(Integer status, long pageNum, long pageSize);

    /** 平台级商品上下架 */
    void productStatus(Long id, Integer status);

    /** 平台订单分页 */
    PageResult<com.digitalmall.vo.OrderVO> orderPage(Integer status, String orderNo, long pageNum, long pageSize);

    /** 售后单分页 */
    PageResult<AfterSaleAdminVO> afterSalePage(Integer status, long pageNum, long pageSize);

    /** 发布公告 */
    void addNotice(NoticeRequest request);

    /** 公告分页 */
    PageResult<NoticeVO> noticePage(Integer status, long pageNum, long pageSize);

    /** 删除公告 */
    void deleteNotice(Long id);

    /** 平台数据总览 */
    StatsOverviewVO statsOverview();

    /** 近 N 日交易趋势（补齐缺失日期） */
    List<TrendPointVO> statsTrend(Integer days);

    /** 操作日志分页 */
    PageResult<OperationLogVO> operationLogPage(String module, String username, long pageNum, long pageSize);
}
