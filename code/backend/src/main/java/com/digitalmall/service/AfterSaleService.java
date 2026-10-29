package com.digitalmall.service;

import com.digitalmall.common.PageResult;
import com.digitalmall.dto.AfterSaleApplyRequest;
import com.digitalmall.vo.AfterSaleDetailVO;
import com.digitalmall.vo.AfterSaleItemVO;

/**
 * 售后模块（退款 / 退货退款 / 换货）
 */
public interface AfterSaleService {

    /** 提交售后申请（用户） */
    void apply(AfterSaleApplyRequest request);

    /** 我的售后列表（可按状态、订单过滤） */
    PageResult<AfterSaleItemVO> myPage(Integer status, Long orderId, long pageNum, long pageSize);

    /** 售后详情（含处理进度） */
    AfterSaleDetailVO detail(Long id);

    /** 撤销售后（仅待商家处理） */
    void cancel(Long id);

    /** 商家端：本店售后列表 */
    PageResult<AfterSaleItemVO> merchantPage(Integer status, long pageNum, long pageSize);

    /** 商家处理：同意即完成并退款，拒绝需填写原因 */
    void merchantHandle(Long id, boolean agree, String rejectReason);

    /** 管理员介入：3 平台介入中 / 4 已完成 */
    void adminHandle(Long id, Integer status, String remark);
}
