package com.digitalmall.service;

import com.digitalmall.common.PageResult;
import com.digitalmall.dto.MerchantApplyRequest;
import com.digitalmall.dto.ProductPublishRequest;
import com.digitalmall.dto.ShopUpdateRequest;
import com.digitalmall.entity.Shop;
import com.digitalmall.vo.MerchantApplyStatusVO;
import com.digitalmall.vo.MerchantOrderVO;
import com.digitalmall.vo.MerchantProductVO;
import com.digitalmall.vo.MerchantStatsVO;

/**
 * 商家服务：入驻 / 店铺 / 商品管理 / 发货 / 统计
 */
public interface MerchantService {

    /** 提交商家入驻申请 */
    void apply(MerchantApplyRequest request);

    /** 查询我的入驻申请状态 */
    MerchantApplyStatusVO applyStatus();

    /** 我的店铺信息 */
    Shop shop();

    /** 修改店铺信息 */
    void updateShop(ShopUpdateRequest request);

    /** 本店商品管理分页（按状态筛选） */
    PageResult<MerchantProductVO> productPage(Integer status, long pageNum, long pageSize);

    /** 发布商品（商品+SKU+参数+图片），返回商品ID */
    Long addProduct(ProductPublishRequest request);

    /** 编辑商品（仅本店） */
    void updateProduct(Long id, ProductPublishRequest request);

    /** 商品上架/下架（1上架 2下架） */
    void updateProductStatus(Long id, Integer status);

    /** 本店订单分页（按状态筛选） */
    PageResult<MerchantOrderVO> orderPage(Integer status, long pageNum, long pageSize);

    /** 订单发货（待发货→待收货） */
    void deliver(Long id, String logisticsNo, String companyName);

    /** 经营数据统计 */
    MerchantStatsVO stats();
}
