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

    /** 登出：删除 Redis 中的登录态，使当前 Token 立即失效 */
    void logout();

    /** 修改密码：校验原密码，成功后强制重新登录 */
    void changePassword(String oldPassword, String newPassword);

    /** 忘记密码：手机号 + 验证码重置，成功后强制重新登录 */
    void forgotPassword(String phone, String code, String newPassword);

    /** 发送短信验证码（Redis 5 分钟，开发环境打印日志模拟发送） */
    void sendSmsCode(String phone, String type);
}
