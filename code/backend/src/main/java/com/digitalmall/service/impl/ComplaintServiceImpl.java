package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.common.BusinessException;
import com.digitalmall.common.PageResult;
import com.digitalmall.dto.ComplaintCreateRequest;
import com.digitalmall.entity.Complaint;
import com.digitalmall.entity.Order;
import com.digitalmall.entity.Shop;
import com.digitalmall.mapper.ComplaintMapper;
import com.digitalmall.mapper.OrderMapper;
import com.digitalmall.mapper.ShopMapper;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.ComplaintService;
import com.digitalmall.vo.ComplaintVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 咨询/投诉实现：用户提交 → 商家回复（0待处理 → 1已回复）
 */
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintMapper complaintMapper;
    private final ShopMapper shopMapper;
    private final OrderMapper orderMapper;

    private Long uid() {
        return UserContext.getUserId();
    }

    private Shop requireShop() {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getMerchantId, uid()));
        if (shop == null) {
            throw new BusinessException(403, "当前账号未开通店铺");
        }
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ComplaintCreateRequest request) {
        Shop shop = shopMapper.selectById(request.getShopId());
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在");
        }
        Integer type = request.getType();
        if (type == null || type < 0 || type > 1) {
            throw new BusinessException(400, "类型只能是咨询或投诉");
        }
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException(400, "请填写咨询/投诉内容");
        }
        if (content.length() > 1000) {
            throw new BusinessException(400, "内容不能超过 1000 字");
        }
        Long orderId = request.getOrderId();
        if (orderId != null) {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new BusinessException(404, "订单不存在");
            }
            if (!order.getUserId().equals(uid())) {
                throw new BusinessException(403, "无权关联该订单");
            }
            if (!order.getShopId().equals(shop.getId())) {
                throw new BusinessException(400, "该订单不属于所选店铺");
            }
        }
        Complaint c = new Complaint();
        c.setUserId(uid());
        c.setShopId(shop.getId());
        c.setOrderId(orderId);
        c.setType(type);
        c.setContent(content);
        c.setStatus(0);
        complaintMapper.insert(c);
    }

    @Override
    public PageResult<ComplaintVO> myPage(Integer status, long pageNum, long pageSize) {
        Page<ComplaintVO> page = new Page<>(pageNum, pageSize);
        IPage<ComplaintVO> ip = complaintMapper.selectUserPage(page, uid(), status);
        return PageResult.of(ip);
    }

    @Override
    public PageResult<ComplaintVO> merchantPage(Integer status, long pageNum, long pageSize) {
        Shop shop = requireShop();
        Page<ComplaintVO> page = new Page<>(pageNum, pageSize);
        IPage<ComplaintVO> ip = complaintMapper.selectMerchantPage(page, shop.getId(), status);
        return PageResult.of(ip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, String content) {
        Complaint c = complaintMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(404, "咨询/投诉不存在");
        }
        Shop shop = requireShop();
        if (!c.getShopId().equals(shop.getId())) {
            throw new BusinessException(403, "无权回复该咨询/投诉");
        }
        if (c.getStatus() != null && c.getStatus() != 0) {
            throw new BusinessException(400, "该咨询/投诉已处理");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(400, "请填写回复内容");
        }
        if (text.length() > 1000) {
            throw new BusinessException(400, "回复内容不能超过 1000 字");
        }
        c.setReplyContent(text);
        c.setReplyTime(LocalDateTime.now());
        c.setStatus(1);
        complaintMapper.updateById(c);
    }
}
