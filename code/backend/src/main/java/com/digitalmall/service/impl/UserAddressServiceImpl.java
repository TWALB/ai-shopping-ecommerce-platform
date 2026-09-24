package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.common.BusinessException;
import com.digitalmall.entity.UserAddress;
import com.digitalmall.mapper.UserAddressMapper;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.UserAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务实现
 */
@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements UserAddressService {

    private final UserAddressMapper userAddressMapper;

    private Long uid() {
        return UserContext.getUserId();
    }

    @Override
    public List<UserAddress> list() {
        return userAddressMapper.selectList(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, uid())
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(UserAddress address) {
        address.setId(null);
        address.setUserId(uid());
        if (address.getIsDefault() == null) {
            address.setIsDefault(0);
        }
        long count = userAddressMapper.selectCount(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, uid()));
        if (count == 0) {
            address.setIsDefault(1); // 首个地址自动设为默认
        }
        if (address.getIsDefault() == 1) {
            clearDefault();
        }
        userAddressMapper.insert(address);
        return address.getId();
    }

    @Override
    public void update(UserAddress address) {
        UserAddress exist = checkOwn(address.getId());
        address.setId(exist.getId());
        address.setUserId(uid());
        if (address.getIsDefault() == null) {
            address.setIsDefault(0);
        }
        if (address.getIsDefault() == 1) {
            clearDefault();
        }
        userAddressMapper.updateById(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UserAddress exist = checkOwn(id);
        userAddressMapper.deleteById(id);
        if (exist.getIsDefault() == 1) {
            // 删除默认地址后，剩余首个地址置为默认
            UserAddress first = userAddressMapper.selectOne(Wrappers.<UserAddress>lambdaQuery()
                    .eq(UserAddress::getUserId, uid())
                    .orderByDesc(UserAddress::getId)
                    .last("limit 1"));
            if (first != null) {
                first.setIsDefault(1);
                userAddressMapper.updateById(first);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        checkOwn(id);
        clearDefault();
        UserAddress addr = userAddressMapper.selectById(id);
        addr.setIsDefault(1);
        userAddressMapper.updateById(addr);
    }

    private void clearDefault() {
        userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, uid())
                .set(UserAddress::getIsDefault, 0));
    }

    private UserAddress checkOwn(Long id) {
        UserAddress addr = userAddressMapper.selectById(id);
        if (addr == null) {
            throw new BusinessException(404, "收货地址不存在");
        }
        if (!addr.getUserId().equals(uid())) {
            throw new BusinessException(403, "无权操作该地址");
        }
        return addr;
    }
}
