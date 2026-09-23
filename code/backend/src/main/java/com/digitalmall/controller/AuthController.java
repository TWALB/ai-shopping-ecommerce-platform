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

    /** 登出（开发阶段：清理 Redis Token 记录） */
    @PostMapping("/logout")
    @RequireRole
    public Result<Void> logout() {
        // TODO 开发阶段实现：删除 Redis key login:token:{userId}
        return Result.success();
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    @RequireRole
    public Result<SysUser> me() {
        return Result.success(authService.getCurrentUser());
    }

    /** 修改密码 */
    @PutMapping("/password")
    @RequireRole
    public Result<Void> changePassword(@RequestParam String oldPassword, @RequestParam String newPassword) {
        // TODO 开发阶段实现：校验原密码后更新
        return Result.success();
    }

    /** 忘记密码（手机号 + 验证码） */
    @PostMapping("/forgot")
    public Result<Void> forgot(@RequestParam String phone, @RequestParam String code,
                               @RequestParam String newPassword) {
        // TODO 开发阶段实现：Redis 校验验证码后重置密码
        return Result.success();
    }

    /** 发送短信验证码（Redis TTL 5分钟） */
    @PostMapping("/sms-code")
    public Result<Void> smsCode(@RequestParam String phone, @RequestParam String type) {
        // TODO 开发阶段实现：生成验证码存入 Redis，模拟发送（打印日志）
        return Result.success();
    }
}
