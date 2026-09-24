package com.digitalmall.service;

import com.digitalmall.entity.UserAddress;

import java.util.List;

/**
 * 收货地址服务
 */
public interface UserAddressService {

    /** 地址列表（默认优先） */
    List<UserAddress> list();

    /** 新增地址（首个自动默认；设默认则清除其他默认） */
    Long add(UserAddress address);

    /** 修改地址（归属校验） */
    void update(UserAddress address);

    /** 删除地址（归属校验；删默认后剩余首个置默认） */
    void delete(Long id);

    /** 设为默认地址 */
    void setDefault(Long id);
}
