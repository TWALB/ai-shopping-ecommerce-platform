package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.common.BusinessException;
import com.digitalmall.common.PageResult;
import com.digitalmall.dto.MerchantApplyRequest;
import com.digitalmall.dto.ProductPublishRequest;
import com.digitalmall.dto.ShopUpdateRequest;
import com.digitalmall.entity.*;
import com.digitalmall.mapper.*;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.MerchantService;
import com.digitalmall.vo.MerchantApplyStatusVO;
import com.digitalmall.vo.MerchantOrderVO;
import com.digitalmall.vo.MerchantProductVO;
import com.digitalmall.vo.MerchantStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家服务实现
 */
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantApplyMapper merchantApplyMapper;
    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductAttrMapper productAttrMapper;
    private final ProductImageMapper productImageMapper;
    private final OrderMapper orderMapper;
    private final LogisticsMapper logisticsMapper;
    private final LogisticsTrackMapper logisticsTrackMapper;
    private final MerchantStatsMapper merchantStatsMapper;

    private Long uid() {
        return UserContext.getUserId();
    }

    // ==================== 入驻 ====================

    @Override
    public void apply(MerchantApplyRequest request) {
        if (request.getRealName() == null || request.getRealName().isBlank()) {
            throw new BusinessException(400, "请填写真实姓名");
        }
        if (request.getContactPhone() == null || request.getContactPhone().isBlank()) {
            throw new BusinessException(400, "请填写联系电话");
        }
        if (request.getShopName() == null || request.getShopName().isBlank()) {
            throw new BusinessException(400, "请填写拟开设店铺名称");
        }
        // 唯一性：同一用户仅一条有效申请（待审核或已通过）
        MerchantApply exist = merchantApplyMapper.selectOne(Wrappers.<MerchantApply>lambdaQuery()
                .eq(MerchantApply::getUserId, uid())
                .in(MerchantApply::getStatus, 0, 1)
                .orderByDesc(MerchantApply::getId)
                .last("limit 1"));
        if (exist != null) {
            throw new BusinessException(409, exist.getStatus() == 1
                    ? "你已是商家，无需重复申请"
                    : "已有待审核的入驻申请，请耐心等待");
        }
        MerchantApply apply = new MerchantApply();
        apply.setUserId(uid());
        apply.setRealName(request.getRealName());
        apply.setIdCard(request.getIdCard());
        apply.setContactPhone(request.getContactPhone());
        apply.setShopName(request.getShopName());
        apply.setShopIntro(request.getShopIntro());
        apply.setQualificationUrl(request.getQualificationUrl());
        apply.setStatus(0);
        merchantApplyMapper.insert(apply);
    }

    @Override
    public MerchantApplyStatusVO applyStatus() {
        MerchantApply apply = merchantApplyMapper.selectOne(Wrappers.<MerchantApply>lambdaQuery()
                .eq(MerchantApply::getUserId, uid())
                .orderByDesc(MerchantApply::getId)
                .last("limit 1"));
        if (apply == null) {
            return null;
        }
        MerchantApplyStatusVO vo = new MerchantApplyStatusVO();
        vo.setStatus(apply.getStatus());
        vo.setRejectReason(apply.getRejectReason());
        vo.setCreatedTime(String.valueOf(apply.getCreatedTime()));
        if (apply.getStatus() == 1) {
            Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery()
                    .eq(Shop::getMerchantId, uid()));
            if (shop != null) {
                vo.setShopId(shop.getId());
            }
        }
        return vo;
    }

    // ==================== 店铺 ====================

    @Override
    public Shop shop() {
        return requireShop();
    }

    @Override
    public void updateShop(ShopUpdateRequest request) {
        Shop shop = requireShop();
        if (request.getShopName() != null && !request.getShopName().isBlank()) {
            shop.setShopName(request.getShopName());
        }
        if (request.getShopLogo() != null) {
            shop.setShopLogo(request.getShopLogo());
        }
        if (request.getShopBanner() != null) {
            shop.setShopBanner(request.getShopBanner());
        }
        if (request.getShopIntro() != null) {
            shop.setShopIntro(request.getShopIntro());
        }
        shopMapper.updateById(shop);
    }

    // ==================== 商品管理 ====================

    @Override
    public PageResult<MerchantProductVO> productPage(Integer status, long pageNum, long pageSize) {
        Shop shop = requireShop();
        Page<MerchantProductVO> page = new Page<>(pageNum, pageSize);
        IPage<MerchantProductVO> ip = productMapper.selectMerchantProductPage(page, shop.getId(), status);
        return PageResult.of(ip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addProduct(ProductPublishRequest request) {
        Shop shop = requireShop();
        validateProduct(request);
        Product product = new Product();
        product.setShopId(shop.getId());
        applyProductFields(product, request);
        product.setSales(0);
        product.setViewCount(0);
        product.setStatus(0);
        productMapper.insert(product);
        saveProductExtras(product.getId(), request);
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Long id, ProductPublishRequest request) {
        Product product = checkProductOwn(id);
        validateProduct(request);
        applyProductFields(product, request);
        productMapper.updateById(product);
        // 重建 SKU / 参数 / 图片（简单可靠）
        productSkuMapper.delete(Wrappers.<ProductSku>lambdaQuery().eq(ProductSku::getProductId, id));
        productAttrMapper.delete(Wrappers.<ProductAttr>lambdaQuery().eq(ProductAttr::getProductId, id));
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery().eq(ProductImage::getProductId, id));
        saveProductExtras(id, request);
    }

    @Override
    public void updateProductStatus(Long id, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(400, "状态仅支持 1上架 / 2下架");
        }
        Product product = checkProductOwn(id);
        product.setStatus(status);
        if (status == 1) {
            product.setShelfTime(LocalDateTime.now());
        }
        productMapper.updateById(product);
    }

    // ==================== 订单发货 ====================

    @Override
    public PageResult<MerchantOrderVO> orderPage(Integer status, long pageNum, long pageSize) {
        Shop shop = requireShop();
        Page<MerchantOrderVO> page = new Page<>(pageNum, pageSize);
        IPage<MerchantOrderVO> ip = orderMapper.selectMerchantOrderPage(page, shop.getId(), status);
        return PageResult.of(ip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliver(Long id, String logisticsNo, String companyName) {
        if (logisticsNo == null || logisticsNo.isBlank()) {
            throw new BusinessException(400, "请填写物流单号");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new BusinessException(400, "请填写物流公司");
        }
        Shop shop = requireShop();
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getShopId().equals(shop.getId())) {
            throw new BusinessException(403, "无权操作该订单");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("仅待发货订单可发货");
        }
        LocalDateTime now = LocalDateTime.now();

        // 写物流 + 首条轨迹
        Logistics logistics = new Logistics();
        logistics.setOrderId(order.getId());
        logistics.setLogisticsNo(logisticsNo);
        logistics.setCompanyName(companyName);
        logistics.setStatus(0);
        logistics.setDeliveryTime(now);
        logisticsMapper.insert(logistics);

        LogisticsTrack track = new LogisticsTrack();
        track.setLogisticsId(logistics.getId());
        track.setTrackInfo("商家已发货，包裹正在揽收中");
        track.setTrackTime(now);
        logisticsTrackMapper.insert(track);

        // 订单 1→2
        order.setStatus(2);
        order.setDeliveryTime(now);
        orderMapper.updateById(order);
    }

    // ==================== 统计 ====================

    @Override
    public MerchantStatsVO stats() {
        Shop shop = requireShop();
        MerchantStatsVO vo = new MerchantStatsVO();
        vo.setTotalSales(merchantStatsMapper.sumSales(shop.getId()));
        vo.setTotalAmount(merchantStatsMapper.sumAmount(shop.getId()));
        vo.setTodayAmount(merchantStatsMapper.sumTodayAmount(shop.getId(), LocalDateTime.now().toLocalDate().atStartOfDay()));
        vo.setPendingShipCount(merchantStatsMapper.countPendingShip(shop.getId()));
        vo.setTopProducts(merchantStatsMapper.topProducts(shop.getId()));
        return vo;
    }

    // ==================== 私有方法 ====================

    /** 当前用户店铺，未入驻则 403 */
    private Shop requireShop() {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().eq(Shop::getMerchantId, uid()));
        if (shop == null) {
            throw new BusinessException(403, "请先完成商家入驻");
        }
        return shop;
    }

    /** 商品必填与数值校验 */
    private void validateProduct(ProductPublishRequest request) {
        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new BusinessException(400, "请填写商品名称");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "请填写正确的售价");
        }
        if (request.getStock() == null || request.getStock() < 0) {
            throw new BusinessException(400, "请填写正确的库存");
        }
    }

    /** 商品头字段拷贝 */
    private void applyProductFields(Product product, ProductPublishRequest r) {
        product.setCategoryId(r.getCategoryId());
        product.setBrandId(r.getBrandId());
        product.setProductName(r.getProductName());
        product.setProductTitle(r.getProductTitle());
        product.setMainImage(r.getMainImage());
        product.setKeywords(r.getKeywords());
        product.setTags(r.getTags());
        product.setBrief(r.getBrief());
        product.setPrice(r.getPrice());
        product.setOriginalPrice(r.getOriginalPrice());
        product.setStock(r.getStock());
    }

    /** 保存 SKU / 参数 / 图片 */
    private void saveProductExtras(Long productId, ProductPublishRequest r) {
        if (r.getSkus() != null) {
            for (ProductPublishRequest.SkuLine line : r.getSkus()) {
                if (line.getSkuName() == null || line.getSkuName().isBlank()) {
                    continue;
                }
                ProductSku sku = new ProductSku();
                sku.setProductId(productId);
                sku.setSkuName(line.getSkuName());
                sku.setPrice(line.getPrice() == null ? r.getPrice() : line.getPrice());
                sku.setStock(line.getStock() == null ? 0 : line.getStock());
                sku.setSkuImage(line.getSkuImage());
                sku.setStatus(1);
                sku.setSales(0);
                productSkuMapper.insert(sku);
            }
        }
        if (r.getAttrs() != null) {
            int sort = 0;
            for (ProductPublishRequest.AttrLine line : r.getAttrs()) {
                if (line.getAttrName() == null || line.getAttrName().isBlank()) {
                    continue;
                }
                ProductAttr attr = new ProductAttr();
                attr.setProductId(productId);
                attr.setAttrName(line.getAttrName());
                attr.setAttrValue(line.getAttrValue());
                attr.setSortOrder(sort++);
                productAttrMapper.insert(attr);
            }
        }
        if (r.getImages() != null) {
            int sort = 0;
            for (ProductPublishRequest.ImageLine line : r.getImages()) {
                if (line.getImageUrl() == null || line.getImageUrl().isBlank()) {
                    continue;
                }
                ProductImage image = new ProductImage();
                image.setProductId(productId);
                image.setImageUrl(line.getImageUrl());
                image.setImageType(line.getImageType() == null ? 0 : line.getImageType());
                image.setSortOrder(sort++);
                productImageMapper.insert(image);
            }
        }
    }

    /** 商品归属校验（仅本店） */
    private Product checkProductOwn(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        Shop shop = shopMapper.selectById(product.getShopId());
        if (shop == null || !shop.getMerchantId().equals(uid())) {
            throw new BusinessException(403, "无权操作该商品");
        }
        return product;
    }
}
