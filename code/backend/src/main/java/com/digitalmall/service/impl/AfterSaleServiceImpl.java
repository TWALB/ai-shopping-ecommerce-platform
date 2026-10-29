package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.common.BusinessException;
import com.digitalmall.common.PageResult;
import com.digitalmall.dto.AfterSaleApplyRequest;
import com.digitalmall.entity.*;
import com.digitalmall.mapper.*;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.AfterSaleService;
import com.digitalmall.vo.AfterSaleDetailVO;
import com.digitalmall.vo.AfterSaleItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 售后模块实现
 * 状态机：0待商家处理 → 1商家同意/2商家拒绝/3平台介入 → 4已完成；0/1/3 可撤销为 5
 */
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements AfterSaleService {

    private final AfterSaleMapper afterSaleMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ShopMapper shopMapper;
    private final PaymentMapper paymentMapper;
    private final OperationLogMapper operationLogMapper;

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private Long uid() {
        return UserContext.getUserId();
    }

    // ==================== 用户端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(AfterSaleApplyRequest request) {
        Order order = orderMapper.selectById(request.getOrderId());
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权对该订单申请售后");
        }
        OrderItem item = orderItemMapper.selectById(request.getOrderItemId());
        if (item == null || !item.getOrderId().equals(order.getId())) {
            throw new BusinessException(400, "订单明细不存在");
        }
        // 同一明细未完成的售后单仅一条（先于订单状态校验，提示更准确）
        Long running = afterSaleMapper.selectCount(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getOrderItemId, item.getId())
                .in(AfterSale::getStatus, 0, 1, 3));
        if (running != null && running > 0) {
            throw new BusinessException(409, "该商品已有进行中的售后申请");
        }
        if (order.getStatus() == null || (order.getStatus() != 2 && order.getStatus() != 3)) {
            throw new BusinessException(400, "仅待收货或已完成订单可申请售后");
        }
        if (request.getType() == null || request.getType() < 0 || request.getType() > 2) {
            throw new BusinessException(400, "售后类型不合法");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(400, "请填写申请原因");
        }

        BigDecimal amount = request.getAmount() == null ? item.getTotalAmount() : request.getAmount();
        if (request.getType() == 2) {
            // 换货不涉及退款
            amount = BigDecimal.ZERO;
        } else {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "退款金额必须大于 0");
            }
            if (amount.compareTo(item.getTotalAmount()) > 0) {
                throw new BusinessException(400, "退款金额不能超过该商品实付金额 ¥" + item.getTotalAmount());
            }
        }

        AfterSale as = new AfterSale();
        as.setAfterSaleNo(genNo());
        as.setOrderId(order.getId());
        as.setOrderItemId(item.getId());
        as.setUserId(uid());
        as.setType(request.getType());
        as.setReason(request.getReason());
        as.setAmount(amount);
        as.setStatus(0);
        afterSaleMapper.insert(as);

        // 订单进入售后中
        order.setStatus(5);
        orderMapper.updateById(order);
    }

    @Override
    public PageResult<AfterSaleItemVO> myPage(Integer status, Long orderId, long pageNum, long pageSize) {
        Page<AfterSaleItemVO> page = new Page<>(pageNum, pageSize);
        IPage<AfterSaleItemVO> ip = afterSaleMapper.selectUserPage(page, uid(), status, orderId);
        return PageResult.of(ip);
    }

    @Override
    public AfterSaleDetailVO detail(Long id) {
        AfterSaleDetailVO vo = afterSaleMapper.selectDetail(id);
        if (vo == null) {
            throw new BusinessException(404, "售后单不存在");
        }
        assertVisible(vo);
        vo.setProgress(buildProgress(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        AfterSale as = afterSaleMapper.selectById(id);
        if (as == null) {
            throw new BusinessException(404, "售后单不存在");
        }
        if (!as.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权撤销该售后单");
        }
        if (as.getStatus() != 0) {
            throw new BusinessException(400, "仅待商家处理的售后单可撤销");
        }
        as.setStatus(5);
        as.setHandleTime(LocalDateTime.now());
        as.setHandleBy(uid());
        afterSaleMapper.updateById(as);
        restoreOrder(as.getOrderId());
    }

    // ==================== 商家端 ====================

    @Override
    public PageResult<AfterSaleItemVO> merchantPage(Integer status, long pageNum, long pageSize) {
        Shop shop = requireShop();
        Page<AfterSaleItemVO> page = new Page<>(pageNum, pageSize);
        IPage<AfterSaleItemVO> ip = afterSaleMapper.selectMerchantPage(page, shop.getId(), status);
        return PageResult.of(ip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void merchantHandle(Long id, boolean agree, String rejectReason) {
        AfterSale as = afterSaleMapper.selectById(id);
        if (as == null) {
            throw new BusinessException(404, "售后单不存在");
        }
        Order order = orderMapper.selectById(as.getOrderId());
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        Shop shop = requireShop();
        if (!order.getShopId().equals(shop.getId())) {
            throw new BusinessException(403, "无权处理该售后单");
        }
        if (as.getStatus() != 0) {
            throw new BusinessException(400, "该售后单已处理");
        }

        as.setHandleTime(LocalDateTime.now());
        as.setHandleBy(uid());
        if (agree) {
            as.setStatus(4);
            afterSaleMapper.updateById(as);
            // 订单回到已完成，并标记支付已退款
            order.setStatus(3);
            if (order.getFinishTime() == null) {
                order.setFinishTime(LocalDateTime.now());
            }
            orderMapper.updateById(order);
            refundPayment(order.getId());
        } else {
            if (rejectReason == null || rejectReason.isBlank()) {
                throw new BusinessException(400, "拒绝售后时必须填写原因");
            }
            as.setStatus(2);
            as.setRejectReason(rejectReason);
            afterSaleMapper.updateById(as);
            restoreOrder(order.getId());
        }
    }

    // ==================== 管理端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminHandle(Long id, Integer status, String remark) {
        if (status == null || (status != 3 && status != 4)) {
            throw new BusinessException(400, "处理状态仅支持 3平台介入 / 4已完成");
        }
        AfterSale as = afterSaleMapper.selectById(id);
        if (as == null) {
            throw new BusinessException(404, "售后单不存在");
        }
        if (as.getStatus() != 2 && as.getStatus() != 3) {
            throw new BusinessException(400, "仅商家拒绝或平台介入中的售后单可由平台处理");
        }
        if (status == 3 && as.getStatus() == 3) {
            throw new BusinessException(400, "该售后单已处于平台介入中");
        }
        as.setStatus(status);
        as.setHandleTime(LocalDateTime.now());
        as.setHandleBy(uid());
        // 仅在最终判定时记录平台意见，避免介入/完成两次重复追加
        if (status == 4 && remark != null && !remark.isBlank()) {
            String prefix = as.getRejectReason() == null ? "" : as.getRejectReason() + "｜";
            as.setRejectReason(prefix + "平台意见：" + remark);
        }
        afterSaleMapper.updateById(as);

        if (status == 4) {
            Order order = orderMapper.selectById(as.getOrderId());
            if (order != null) {
                order.setStatus(3);
                if (order.getFinishTime() == null) {
                    order.setFinishTime(LocalDateTime.now());
                }
                orderMapper.updateById(order);
                refundPayment(order.getId());
            }
        }
        log("售后管理", status == 3 ? "平台介入 售后单" + as.getAfterSaleNo()
                : "平台处理完成 售后单" + as.getAfterSaleNo());
    }

    // ==================== 私有方法 ====================

    /** 详情可见性：本人 / 本店商家 / 管理员 */
    private void assertVisible(AfterSaleDetailVO vo) {
        Long current = uid();
        if (current == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (current.equals(vo.getUserId())) {
            return;
        }
        UserContext.CurrentUser user = UserContext.get();
        if (user != null && user.role() != null && user.role() == 2) {
            return;
        }
        if (user != null && user.role() != null && user.role() == 1) {
            Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getMerchantId, current));
            Order order = orderMapper.selectById(vo.getOrderId());
            if (shop != null && order != null && shop.getId().equals(order.getShopId())) {
                return;
            }
        }
        throw new BusinessException(403, "无权查看该售后单");
    }

    /** 处理进度节点 */
    private List<AfterSaleDetailVO.ProgressVO> buildProgress(AfterSaleDetailVO vo) {
        List<AfterSaleDetailVO.ProgressVO> list = new ArrayList<>();
        list.add(node("提交申请", typeText(vo.getType()) + "：" + vo.getReason(),
                format(vo.getCreatedTime()), true));

        boolean handled = vo.getStatus() != null && vo.getStatus() != 0;
        String desc;
        switch (vo.getStatus() == null ? -1 : vo.getStatus()) {
            case 0 -> desc = "等待商家处理";
            case 1 -> desc = "商家已同意，等待退款";
            case 2 -> desc = "商家拒绝：" + (vo.getRejectReason() == null ? "未填写原因" : vo.getRejectReason());
            case 3 -> desc = "平台介入处理中";
            case 4 -> desc = "售后已完成，退款已原路退回";
            case 5 -> desc = "用户已撤销申请";
            default -> desc = "—";
        }
        list.add(node("商家处理", desc, format(vo.getHandleTime()), handled));

        boolean finished = vo.getStatus() != null && (vo.getStatus() == 4 || vo.getStatus() == 5);
        list.add(node(finished ? "售后结束" : "结果", finished ? desc : "处理中…",
                finished ? format(vo.getHandleTime()) : "", finished));
        return list;
    }

    private AfterSaleDetailVO.ProgressVO node(String title, String desc, String time, boolean active) {
        AfterSaleDetailVO.ProgressVO n = new AfterSaleDetailVO.ProgressVO();
        n.setTitle(title);
        n.setDescription(desc);
        n.setTime(time);
        n.setActive(active);
        return n;
    }

    private String typeText(Integer type) {
        if (type == null) return "售后";
        return switch (type) {
            case 0 -> "仅退款";
            case 1 -> "退货退款";
            case 2 -> "换货";
            default -> "售后";
        };
    }

    private String format(LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 无其他进行中售后时，把订单恢复为售后前状态 */
    private void restoreOrder(Long orderId) {
        Long running = afterSaleMapper.selectCount(Wrappers.<AfterSale>lambdaQuery()
                .eq(AfterSale::getOrderId, orderId)
                .in(AfterSale::getStatus, 0, 1, 3));
        if (running != null && running > 0) {
            return;
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 5) {
            return;
        }
        order.setStatus(order.getFinishTime() != null ? 3 : 2);
        orderMapper.updateById(order);
    }

    /** 同意售后时把支付流水标记为已退款（status 2） */
    private void refundPayment(Long orderId) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .orderByDesc(Payment::getId)
                .last("limit 1"));
        if (payment != null && payment.getStatus() != null && payment.getStatus() == 1) {
            payment.setStatus(2);
            paymentMapper.updateById(payment);
        }
    }

    private Shop requireShop() {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getMerchantId, uid()));
        if (shop == null) {
            throw new BusinessException(403, "请先完成商家入驻");
        }
        return shop;
    }

    private void log(String module, String action) {
        OperationLog log = new OperationLog();
        log.setUserId(uid());
        log.setModule(module);
        log.setAction(action);
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                log.setIp(attrs.getRequest().getRemoteAddr());
            }
        } catch (Exception ignored) {
        }
        operationLogMapper.insert(log);
    }

    private String genNo() {
        return "AS" + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
