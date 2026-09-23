package com.digitalmall.service;

import com.digitalmall.dto.LoginRequest;
import com.digitalmall.dto.RegisterRequest;
import com.digitalmall.entity.SysUser;
import com.digitalmall.vo.LoginVO;

/**
 * 认证服务：注册 / 登录 / 当前用户
 */
public interface AuthService {

    /** 注册：用户名唯一校验 + BCrypt 加密 */
    LoginVO register(RegisterRequest request);

    /** 登录：校验密码，返回 Token */
    LoginVO login(LoginRequest request);

    /** 当前登录用户 */
    SysUser getCurrentUser();
}
