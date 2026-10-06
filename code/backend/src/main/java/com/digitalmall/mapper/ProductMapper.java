package com.digitalmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digitalmall.entity.Product;
import com.digitalmall.vo.MerchantProductVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商品 Mapper
 */
public interface ProductMapper extends BaseMapper<Product> {

    /** 商家本店商品管理分页（含 SKU 数，按状态筛选） */
    @Select("<script>" +
            "SELECT p.id, p.product_name, p.main_image, p.price, p.stock, p.sales, p.status, " +
            "p.shelf_time, p.created_time, " +
            "(SELECT COUNT(*) FROM product_sku ps WHERE ps.product_id = p.id) AS sku_count " +
            "FROM product p " +
            "WHERE p.shop_id = #{shopId} " +
            "<if test='status != null'> AND p.status = #{status} </if>" +
            "ORDER BY p.created_time DESC" +
            "</script>")
    IPage<MerchantProductVO> selectMerchantProductPage(Page<MerchantProductVO> page,
                                                       @Param("shopId") Long shopId,
                                                       @Param("status") Integer status);
}
