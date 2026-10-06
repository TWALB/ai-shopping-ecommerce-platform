package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.common.BusinessException;
import com.digitalmall.common.PageResult;
import com.digitalmall.dto.SubmitCommentRequest;
import com.digitalmall.entity.Comment;
import com.digitalmall.entity.Order;
import com.digitalmall.entity.OrderItem;
import com.digitalmall.entity.Shop;
import com.digitalmall.mapper.CommentMapper;
import com.digitalmall.mapper.OrderItemMapper;
import com.digitalmall.mapper.OrderMapper;
import com.digitalmall.mapper.ShopMapper;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.CommentService;
import com.digitalmall.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 评价服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    /**
     * 正式审核流程：评价提交后默认待审核（status=0），
     * 由管理端在「评价审核」页审核通过（status=1）后在商品详情展示，或判违规删除（status=2）。
     * 如需跳过审核直接展示，可临时改为 true。
     */
    private static final boolean DEMO_AUTO_PUBLISH = false;

    private final CommentMapper commentMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ShopMapper shopMapper;

    private Long uid() {
        return UserContext.getUserId();
    }

    @Override
    public void submit(SubmitCommentRequest request) {
        if (request.getOrderId() == null || request.getOrderItemId() == null) {
            throw new BusinessException(400, "缺少订单或明细参数");
        }
        int ps = request.getProductScore() == null ? 5 : request.getProductScore();
        int ls = request.getLogisticsScore() == null ? 5 : request.getLogisticsScore();
        if (ps < 1 || ps > 5 || ls < 1 || ls > 5) {
            throw new BusinessException(400, "评分范围应为 1-5");
        }

        // 订单校验：存在 / 归属 / 已完成
        Order order = orderMapper.selectById(request.getOrderId());
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权评价该订单");
        }
        if (order.getStatus() != 3) {
            throw new BusinessException("订单完成后才能评价");
        }

        // 明细校验：属于该订单
        OrderItem item = orderItemMapper.selectById(request.getOrderItemId());
        if (item == null || !item.getOrderId().equals(order.getId())) {
            throw new BusinessException(400, "订单明细不存在或不属于该订单");
        }

        // 唯一性：同一明细仅可评价一次
        long existed = commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getOrderId, order.getId())
                .eq(Comment::getOrderItemId, item.getId()));
        if (existed > 0) {
            throw new BusinessException("该商品已评价过");
        }

        // 插入评价
        Comment comment = new Comment();
        comment.setOrderId(order.getId());
        comment.setOrderItemId(item.getId());
        comment.setProductId(item.getProductId());
        comment.setUserId(uid());
        comment.setProductScore(ps);
        comment.setLogisticsScore(ls);
        comment.setContent(request.getContent());
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            comment.setImageUrls(String.join(",", request.getImageUrls()));
        }
        comment.setIsAnonymous(Boolean.TRUE.equals(request.getIsAnonymous()) ? 1 : 0);
        comment.setStatus(DEMO_AUTO_PUBLISH ? 1 : 0);
        commentMapper.insert(comment);
    }

    @Override
    public PageResult<CommentVO> productComments(Long productId, long pageNum, long pageSize) {
        Page<CommentVO> page = new Page<>(pageNum, pageSize);
        IPage<CommentVO> ip = commentMapper.selectProductComments(page, productId);
        return PageResult.of(ip);
    }

    @Override
    public PageResult<CommentVO> managePage(Integer status, String productName, long pageNum, long pageSize) {
        Integer role = UserContext.get() == null ? null : UserContext.get().role();
        Long shopId = null;
        if (role != null && role == 1) {
            // 商家：限定本店
            Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getMerchantId, uid()));
            if (shop == null) {
                throw new BusinessException(403, "请先完成商家入驻");
            }
            shopId = shop.getId();
        }
        Page<CommentVO> page = new Page<>(pageNum, pageSize);
        IPage<CommentVO> ip = commentMapper.selectManageComments(page, shopId, status, productName);
        return PageResult.of(ip);
    }

    @Override
    public void reply(Long id, String replyContent) {
        if (replyContent == null || replyContent.isBlank()) {
            throw new BusinessException(400, "回复内容不能为空");
        }
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(404, "评价不存在");
        }
        // 商家只能回复本店商品评价
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getMerchantId, uid()));
        if (shop != null) {
            Long count = commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
                    .eq(Comment::getId, id)
                    .apply("EXISTS (SELECT 1 FROM product p WHERE p.id = `comment`.product_id AND p.shop_id = {0})", shop.getId()));
            if (count == 0) {
                throw new BusinessException(403, "只能回复本店商品的评价");
            }
        }
        comment.setReplyContent(replyContent);
        comment.setReplyTime(LocalDateTime.now());
        commentMapper.updateById(comment);
    }

    @Override
    public void audit(Long id, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(400, "审核状态仅支持 1展示 / 2违规删除");
        }
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(404, "评价不存在");
        }
        comment.setStatus(status);
        commentMapper.updateById(comment);
    }
}
