package com.digitalmall.service;

import com.digitalmall.common.PageResult;
import com.digitalmall.dto.SubmitCommentRequest;
import com.digitalmall.vo.CommentVO;

/**
 * 评价服务
 */
public interface CommentService {

    /** 发表评价（订单完成后；同一明细仅可评一次） */
    void submit(SubmitCommentRequest request);

    /** 商品已展示评价分页（公开） */
    PageResult<CommentVO> productComments(Long productId, long pageNum, long pageSize);

    /** 评价管理分页（商家看本店 / 管理员看全部） */
    PageResult<CommentVO> managePage(Integer status, String productName, long pageNum, long pageSize);

    /** 商家回复评价 */
    void reply(Long id, String replyContent);

    /** 管理员审核评价（1展示 / 2违规删除） */
    void audit(Long id, Integer status);
}
