package com.digitalmall.service;

import com.digitalmall.common.PageResult;
import com.digitalmall.dto.CreateOrderRequest;
import com.digitalmall.vo.CreateOrderVO;
import com.digitalmall.vo.OrderDetailVO;
import com.digitalmall.vo.OrderVO;
import com.digitalmall.vo.TrackVO;

import java.util.List;

/**
 * 订单服务（核心交易逻辑：库存扣减 + 快照 + 状态机）
 */
public interface OrderService {

    /** 创建订单（事务：预扣库存 → 写订单/明细快照 → 按店铺分单 → 清空勾选） */
    List<CreateOrderVO> create(CreateOrderRequest request);

    /** 我的订单分页（按状态筛选） */
    PageResult<OrderVO> page(Integer status, long pageNum, long pageSize);

    /** 订单详情（明细/收货快照/物流/支付） */
    OrderDetailVO detail(Long id);

    /** 取消订单（仅待付款，回补库存） */
    void cancel(Long id);

    /** 确认收货（待收货→已完成） */
    void confirm(Long id);

    /** 物流轨迹（时间倒序） */
    List<TrackVO> track(Long id);
}
