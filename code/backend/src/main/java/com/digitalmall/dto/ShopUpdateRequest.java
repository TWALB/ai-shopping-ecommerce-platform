package com.digitalmall.dto;

import lombok.Data;

/**
 * 修改店铺信息
 */
@Data
public class ShopUpdateRequest {

    private String shopName;

    private String shopLogo;

    private String shopBanner;

    private String shopIntro;
}
