package com.digitalmall.vo;

import com.digitalmall.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录/注册返回：Token + 用户信息
 */
@Data
@AllArgsConstructor
public class LoginVO {

    private String token;

    private SysUser user;
}
