package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.common.BusinessException;
import com.digitalmall.common.PageResult;
import com.digitalmall.dto.CreateOrderRequest;
import com.digitalmall.entity.*;
import com.digitalmall.mapper.*;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.OrderService;
import com.digitalmall.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务实现：核心交易逻辑（库存预扣 + 快照 + 分单 + 状态机）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final UserAddressMapper userAddressMapper;
    private final LogisticsMapper logisticsMapper;
    private final LogisticsTrackMapper logisticsTrackMapper;
    private final PaymentMapper paymentMapper;

    private Long uid() {
        return UserContext.getUserId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CreateOrderVO> create(CreateOrderRequest request) {
        if (request.getAddressId() == null) {
            throw new BusinessException(400, "请选择收货地址");
        }
        UserAddress addr = userAddressMapper.selectById(request.getAddressId());
        if (addr == null) {
            throw new BusinessException(404, "收货地址不存在");
        }
        if (!addr.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权使用该收货地址");
        }

        // 1. 收集购买行：购物车勾选项 或 直接购买明细
        List<CartVO> lines = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getFromCart())) {
            lines = cartMapper.selectSelectedVOList(uid());
            if (lines.isEmpty()) {
                throw new BusinessException("请先勾选要结算的商品");
            }
        } else {
            if (request.getItemList() == null || request.getItemList().isEmpty()) {
                throw new BusinessException(400, "请选择要购买的商品");
            }
            for (CreateOrderRequest.OrderItemLine line : request.getItemList()) {
                lines.add(buildLineFromSku(line.getSkuId(), line.getQuantity()));
            }
        }

        // 2. 按店铺分单（一个店铺一单）
        Map<Long, List<CartVO>> byShop = lines.stream().collect(Collectors.groupingBy(CartVO::getShopId));
        List<CreateOrderVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<CartVO>> entry : byShop.entrySet()) {
            result.add(createOneOrder(entry.getKey(), entry.getValue(), addr, request.getRemark()));
        }

        // 3. 购物车结算 → 清空勾选项
        if (Boolean.TRUE.equals(request.getFromCart())) {
            cartMapper.delete(Wrappers.<Cart>lambdaQuery()
                    .eq(Cart::getUserId, uid())
                    .eq(Cart::getIsSelected, 1));
        }
        return result;
    }

    /** 生成单个店铺订单：预扣库存 → 订单头 → 明细快照 → 销量累加 */
    private CreateOrderVO createOneOrder(Long shopId, List<CartVO> lines, UserAddress addr, String remark) {
        // 预扣库存（原子条件更新，防超卖）
        for (CartVO line : lines) {
            int updated = productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                    .setSql("stock = stock - {0}", line.getQuantity())
                    .eq(ProductSku::getId, line.getSkuId())
                    .ge(ProductSku::getStock, line.getQuantity()));
            if (updated == 0) {
                throw new BusinessException("商品「" + line.getProductName() + "」库存不足");
            }
        }

        // 订单头（地址快照）
        Order order = new Order();
        order.setOrderNo(genOrderNo());
        order.setUserId(uid());
        order.setShopId(shopId);
        order.setReceiverName(addr.getReceiverName());
        order.setReceiverPhone(addr.getReceiverPhone());
        order.setReceiverAddress(addr.getProvince() + addr.getCity() + addr.getDistrict() + addr.getDetailAddress());
        order.setStatus(0);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setRemark(remark);
        BigDecimal total = lines.stream()
                .map(l -> l.getPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        order.setPayAmount(total);
        orderMapper.insert(order);

        // 明细快照 + 商品销量累加
        for (CartVO line : lines) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(line.getProductId());
            item.setSkuId(line.getSkuId());
            item.setProductName(line.getProductName());
            item.setSkuName(line.getSkuName());
            item.setProductImage(line.getMainImage());
            item.setPrice(line.getPrice());
            item.setQuantity(line.getQuantity());
            item.setTotalAmount(line.getPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
            orderItemMapper.insert(item);

            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .setSql("sales = sales + {0}", line.getQuantity())
                    .eq(Product::getId, line.getProductId()));
        }

        CreateOrderVO vo = new CreateOrderVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPayAmount(order.getPayAmount());
        vo.setStatus(order.getStatus());
        return vo;
    }

    @Override
    public PageResult<OrderVO> page(Integer status, long pageNum, long pageSize) {
        Page<OrderVO> page = new Page<>(pageNum, pageSize);
        IPage<OrderVO> ip = orderMapper.selectOrderVOList(page, uid(), status);
        return PageResult.of(ip);
    }

    @Override
    public OrderDetailVO detail(Long id) {
        Order order = checkOwnOrder(id);
        OrderDetailVO vo = new OrderDetailVO();
        BeanUtils.copyProperties(order, vo);

        // 收货人快照
        OrderDetailVO.ReceiverVO receiver = new OrderDetailVO.ReceiverVO();
        receiver.setName(order.getReceiverName());
        receiver.setPhone(order.getReceiverPhone());
        receiver.setAddress(order.getReceiverAddress());
        vo.setReceiver(receiver);

        // 店铺名
        Shop shop = shopMapper.selectById(order.getShopId());
        if (shop != null) {
            vo.setShopName(shop.getShopName());
        }

        // 明细
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, id));
        vo.setItems(items.stream().map(it -> {
            OrderDetailVO.ItemVO iv = new OrderDetailVO.ItemVO();
            BeanUtils.copyProperties(it, iv);
            iv.setOrderItemId(it.getId());
            return iv;
        }).collect(Collectors.toList()));

        // 物流
        Logistics logistics = logisticsMapper.selectOne(Wrappers.<Logistics>lambdaQuery()
                .eq(Logistics::getOrderId, id));
        if (logistics != null) {
            OrderDetailVO.LogisticsVO lv = new OrderDetailVO.LogisticsVO();
            lv.setCompanyName(logistics.getCompanyName());
            lv.setLogisticsNo(logistics.getLogisticsNo());
            lv.setStatus(logistics.getStatus());
            lv.setDeliveryTime(logistics.getDeliveryTime());
            vo.setLogistics(lv);
        }

        // 支付
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, id)
                .orderByDesc(Payment::getId)
                .last("limit 1"));
        if (payment != null) {
            OrderDetailVO.PaymentVO pv = new OrderDetailVO.PaymentVO();
            pv.setPaymentNo(payment.getPaymentNo());
            pv.setStatus(payment.getStatus());
            pv.setPayTime(payment.getPayTime());
            vo.setPayment(pv);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        Order order = checkOwnOrder(id);
        if (order.getStatus() != 0) {
            throw new BusinessException("仅待付款订单可取消");
        }
        // 回补库存与销量
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, id));
        for (OrderItem item : items) {
            productSkuMapper.update(null, new LambdaUpdateWrapper<ProductSku>()
                    .setSql("stock = stock + {0}", item.getQuantity())
                    .eq(ProductSku::getId, item.getSkuId()));
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .setSql("sales = GREATEST(sales - {0}, 0)", item.getQuantity())
                    .eq(Product::getId, item.getProductId()));
        }
        order.setStatus(4);
        orderMapper.updateById(order);
    }

    @Override
    public void confirm(Long id) {
        Order order = checkOwnOrder(id);
        if (order.getStatus() != 2) {
            throw new BusinessException("仅待收货订单可确认收货");
        }
        order.setStatus(3);
        order.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    public List<TrackVO> track(Long id) {
        checkOwnOrder(id);
        Logistics logistics = logisticsMapper.selectOne(Wrappers.<Logistics>lambdaQuery()
                .eq(Logistics::getOrderId, id));
        if (logistics == null) {
            return Collections.emptyList();
        }
        List<LogisticsTrack> tracks = logisticsTrackMapper.selectList(Wrappers.<LogisticsTrack>lambdaQuery()
                .eq(LogisticsTrack::getLogisticsId, logistics.getId())
                .orderByDesc(LogisticsTrack::getTrackTime));
        return tracks.stream().map(t -> {
            TrackVO tv = new TrackVO();
            tv.setTrackInfo(t.getTrackInfo());
            tv.setTrackTime(t.getTrackTime());
            return tv;
        }).collect(Collectors.toList());
    }

    /** 直接购买行组装（校验 SKU 与商品状态） */
    private CartVO buildLineFromSku(Long skuId, Integer quantity) {
        ProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(404, "商品规格不存在");
        }
        Product product = productMapper.selectById(sku.getProductId());
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(404, "商品不存在或已下架");
        }
        CartVO v = new CartVO();
        v.setSkuId(skuId);
        v.setProductId(product.getId());
        v.setProductName(product.getProductName());
        v.setMainImage(product.getMainImage());
        v.setSkuName(sku.getSkuName());
        v.setPrice(sku.getPrice());
        v.setQuantity(quantity);
        v.setStock(sku.getStock());
        v.setStatus(product.getStatus());
        v.setShopId(product.getShopId());
        return v;
    }

    /** 订单号：yyyyMMddHHmmss + 6位随机 */
    private String genOrderNo() {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    /** 订单归属校验 */
    private Order checkOwnOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权操作该订单");
        }
        return order;
    }
}
