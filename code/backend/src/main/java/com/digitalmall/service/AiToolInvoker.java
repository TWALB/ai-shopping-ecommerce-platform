package com.digitalmall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.entity.Logistics;
import com.digitalmall.entity.LogisticsTrack;
import com.digitalmall.entity.Order;
import com.digitalmall.entity.Product;
import com.digitalmall.entity.ProductAttr;
import com.digitalmall.mapper.LogisticsMapper;
import com.digitalmall.mapper.LogisticsTrackMapper;
import com.digitalmall.mapper.OrderMapper;
import com.digitalmall.mapper.ProductAttrMapper;
import com.digitalmall.mapper.ProductMapper;
import com.digitalmall.vo.ProductBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置工具的真实执行体。
 *
 * 为什么不做成「注册表里填一个 HTTP 端点，后端转发」：
 * 那样等于把内网接口暴露给管理端配置，安全边界不可控，也演示不了「同一份数据两种用法」。
 * 这里用 tool_code → Java 方法的静态绑定，注册表只管元数据（描述/参数/开关），
 * 实现留在代码里，管理端能改的是「模型怎么理解这个工具」，不是「这个工具打哪里」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolInvoker {

    private final ProductRetrievalService retrievalService;
    private final ProductMapper productMapper;
    private final ProductAttrMapper productAttrMapper;
    private final OrderMapper orderMapper;
    private final LogisticsMapper logisticsMapper;
    private final LogisticsTrackMapper logisticsTrackMapper;

    private static final String[] ORDER_STATUS = {"待付款", "待发货", "待收货", "已完成", "已取消", "售后中"};

    /** 该 tool_code 是否有对应实现（注册表可以登记纯说明型工具，但不允许执行） */
    public boolean supported(String code) {
        return switch (code) {
            case "searchProduct", "getProductDetail", "getPrice", "getOrderStatus", "getTrack" -> true;
            default -> false;
        };
    }

    public Map<String, Object> invoke(String code, Map<String, Object> params) {
        Map<String, Object> p = params == null ? Map.of() : params;
        return switch (code) {
            case "searchProduct" -> searchProduct(p);
            case "getProductDetail" -> getProductDetail(p);
            case "getPrice" -> getPrice(p);
            case "getOrderStatus" -> getOrderStatus(p);
            case "getTrack" -> getTrack(p);
            default -> throw new IllegalArgumentException("工具未实现：" + code);
        };
    }

    // ==================== 商品域 ====================

    private Map<String, Object> searchProduct(Map<String, Object> p) {
        String keyword = str(p, "keyword");
        String category = str(p, "category");
        String brand = str(p, "brand");
        double budget = num(p, "budget");
        int topK = (int) Math.max(1, Math.min(num(p, "topK") <= 0 ? 5 : num(p, "topK"), 20));

        if (keyword.isBlank() && category.isBlank() && budget <= 0) {
            throw new IllegalArgumentException("至少提供 keyword / category / budget 之一");
        }

        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("category", category);
        slots.put("budget", budget);
        slots.put("keyword", keyword);
        slots.put("brand", brand);
        String content = (keyword + " " + category + " " + brand).trim();

        long t0 = System.currentTimeMillis();
        List<ProductBriefVO> list = retrievalService.retrieve(slots, content, "find_product", true);
        long cost = System.currentTimeMillis() - t0;

        List<Map<String, Object>> items = new ArrayList<>();
        for (ProductBriefVO vo : list.stream().limit(topK).toList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", vo.getId());
            m.put("productName", vo.getProductName());
            m.put("price", vo.getPrice());
            m.put("sales", vo.getSales());
            m.put("similarity", vo.getSimilarity());
            items.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", items.size());
        out.put("retrievalCostMs", cost);
        out.put("slots", slots);
        out.put("items", items);
        return out;
    }

    private Map<String, Object> getProductDetail(Map<String, Object> p) {
        Long id = longVal(p, "productId");
        if (id == null) {
            throw new IllegalArgumentException("缺少参数 productId");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在：" + id);
        }
        List<ProductAttr> attrs = productAttrMapper.selectList(
                Wrappers.<ProductAttr>lambdaQuery()
                        .eq(ProductAttr::getProductId, id)
                        .orderByAsc(ProductAttr::getSortOrder));

        List<Map<String, Object>> attrList = new ArrayList<>();
        for (ProductAttr a : attrs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.getAttrName());
            m.put("value", a.getAttrValue());
            attrList.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productId", product.getId());
        out.put("productName", product.getProductName());
        out.put("productTitle", product.getProductTitle());
        out.put("brief", product.getBrief());
        out.put("price", product.getPrice());
        out.put("stock", product.getStock());
        out.put("categoryId", product.getCategoryId());
        out.put("brandId", product.getBrandId());
        out.put("status", product.getStatus());
        out.put("attrCount", attrList.size());
        out.put("attrs", attrList);
        return out;
    }

    private Map<String, Object> getPrice(Map<String, Object> p) {
        Long id = longVal(p, "productId");
        if (id == null) {
            throw new IllegalArgumentException("缺少参数 productId");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在：" + id);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productId", product.getId());
        out.put("productName", product.getProductName());
        out.put("price", product.getPrice());
        out.put("originalPrice", product.getOriginalPrice());
        out.put("stock", product.getStock());
        out.put("sales", product.getSales());
        out.put("inStock", product.getStock() != null && product.getStock() > 0);
        return out;
    }

    // ==================== 业务域 ====================

    private Map<String, Object> getOrderStatus(Map<String, Object> p) {
        Order order = loadOrder(p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", order.getOrderNo());
        out.put("status", order.getStatus());
        out.put("statusText", statusText(order.getStatus()));
        out.put("payAmount", order.getPayAmount());
        out.put("createdTime", order.getCreatedTime());
        out.put("payTime", order.getPayTime());
        out.put("deliveryTime", order.getDeliveryTime());
        out.put("receiverName", order.getReceiverName());
        out.put("receiverAddress", order.getReceiverAddress());
        return out;
    }

    private Map<String, Object> getTrack(Map<String, Object> p) {
        Order order = loadOrder(p);
        Logistics logistics = logisticsMapper.selectOne(
                Wrappers.<Logistics>lambdaQuery().eq(Logistics::getOrderId, order.getId()).last("limit 1"));
        if (logistics == null) {
            throw new IllegalArgumentException("该订单还没有物流记录，当前状态：" + statusText(order.getStatus()));
        }
        List<LogisticsTrack> tracks = logisticsTrackMapper.selectList(
                Wrappers.<LogisticsTrack>lambdaQuery()
                        .eq(LogisticsTrack::getLogisticsId, logistics.getId())
                        .orderByDesc(LogisticsTrack::getTrackTime));

        List<Map<String, Object>> list = new ArrayList<>();
        for (LogisticsTrack t : tracks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("trackTime", t.getTrackTime());
            m.put("trackInfo", t.getTrackInfo());
            list.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", order.getOrderNo());
        out.put("orderStatus", statusText(order.getStatus()));
        out.put("companyName", logistics.getCompanyName());
        out.put("logisticsNo", logistics.getLogisticsNo());
        out.put("deliveryTime", logistics.getDeliveryTime());
        out.put("trackCount", list.size());
        out.put("tracks", list);
        return out;
    }

    private Order loadOrder(Map<String, Object> p) {
        String orderNo = str(p, "orderNo");
        if (orderNo.isBlank()) {
            throw new IllegalArgumentException("缺少参数 orderNo");
        }
        Order order = orderMapper.selectOne(
                Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo).last("limit 1"));
        if (order == null) {
            throw new IllegalArgumentException("订单不存在：" + orderNo);
        }
        return order;
    }

    private String statusText(Integer status) {
        if (status == null || status < 0 || status >= ORDER_STATUS.length) {
            return "未知";
        }
        return ORDER_STATUS[status];
    }

    // ==================== 参数取值 ====================

    private String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private double num(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long longVal(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + key + " 必须是整数");
        }
    }
}
