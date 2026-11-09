package com.digitalmall.service;

import com.digitalmall.common.PageResult;
import com.digitalmall.dto.ComplaintCreateRequest;
import com.digitalmall.vo.ComplaintVO;

/**
 * 咨询/投诉（商家端客户管理）
 */
public interface ComplaintService {

    /** 用户提交咨询/投诉 */
    void create(ComplaintCreateRequest request);

    /** 我提交的咨询/投诉 */
    PageResult<ComplaintVO> myPage(Integer status, long pageNum, long pageSize);

    /** 本店收到的咨询/投诉（商家） */
    PageResult<ComplaintVO> merchantPage(Integer status, long pageNum, long pageSize);

    /** 商家回复（待处理 → 已回复） */
    void reply(Long id, String content);
}
