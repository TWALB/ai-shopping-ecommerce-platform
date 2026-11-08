package com.digitalmall.controller;

import com.digitalmall.common.Result;
import com.digitalmall.dto.LoginRequest;
import com.digitalmall.dto.RegisterRequest;
import com.digitalmall.entity.SysUser;
import com.digitalmall.security.RequireRole;
import com.digitalmall.service.AuthService;
import com.digitalmall.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证模块（接口文档：一、认证模块）
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 用户注册 */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 登出（删除 Redis 中的登录态，当前 Token 立即失效） */
    @PostMapping("/logout")
    @RequireRole
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    @RequireRole
    public Result<SysUser> me() {
        return Result.success(authService.getCurrentUser());
    }

    /** 修改密码（校验原密码；成功后强制重新登录） */
    @PutMapping("/password")
    @RequireRole
    public Result<Void> changePassword(@RequestParam String oldPassword, @RequestParam String newPassword) {
        authService.changePassword(oldPassword, newPassword);
        return Result.success();
    }

    /** 忘记密码（手机号 + 验证码） */
    @PostMapping("/forgot")
    public Result<Void> forgot(@RequestParam String phone, @RequestParam String code,
                               @RequestParam String newPassword) {
        authService.forgotPassword(phone, code, newPassword);
        return Result.success();
    }

    /** 发送短信验证码（Redis TTL 5 分钟，开发环境打印日志模拟发送） */
    @PostMapping("/sms-code")
    public Result<Void> smsCode(@RequestParam String phone, @RequestParam String type) {
        authService.sendSmsCode(phone, type);
        return Result.success();
    }
}
