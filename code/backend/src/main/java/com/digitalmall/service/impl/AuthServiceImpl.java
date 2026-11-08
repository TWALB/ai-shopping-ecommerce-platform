package com.digitalmall.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.digitalmall.common.BusinessException;
import com.digitalmall.dto.LoginRequest;
import com.digitalmall.dto.RegisterRequest;
import com.digitalmall.entity.SysUser;
import com.digitalmall.mapper.SysUserMapper;
import com.digitalmall.security.JwtUtil;
import com.digitalmall.security.UserContext;
import com.digitalmall.service.AuthService;
import com.digitalmall.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现：注册 / 登录 / 登出 / 改密 / 找回密码 / 短信验证码
 *
 * 登录态采用「JWT + Redis」双校验：
 * - 登录成功时把 Token 写入 login:token:{userId}，TTL 与 JWT 有效期一致；
 * - 拦截器每次请求比对 Redis 中的 Token，退出登录、改密、找回密码都会删除该 Key，
 *   从而让已签发的 Token 立即失效（单端登录：同一账号新登录会顶掉旧 Token）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Redis 登录态：login:token:{userId} -> token */
    private static final String TOKEN_KEY = "login:token:";
    /** Redis 验证码：sms:code:{type}:{phone} -> code */
    private static final String SMS_CODE_KEY = "sms:code:";
    /** Redis 发送频率限制：sms:limit:{type}:{phone} */
    private static final String SMS_LIMIT_KEY = "sms:limit:";
    private static final Duration SMS_TTL = Duration.ofMinutes(5);
    private static final Duration SMS_LIMIT_TTL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginVO register(RegisterRequest request) {
        Long exists = sysUserMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "用户名已存在");
        }
        // 填了验证码就校验（文档：验证码可选）
        if (request.getCode() != null && !request.getCode().isBlank()) {
            if (request.getPhone() == null || request.getPhone().isBlank()) {
                throw new BusinessException(400, "校验验证码前请先填写手机号");
            }
            verifyCode("register", request.getPhone(), request.getCode());
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null ? request.getUsername() : request.getNickname());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setGender(0);
        user.setRole(0);           // 默认普通用户
        user.setStatus(1);
        sysUserMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        cacheToken(user.getId(), token);
        return new LoginVO(token, user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        cacheToken(user.getId(), token);
        return new LoginVO(token, user);
    }

    @Override
    public SysUser getCurrentUser() {
        Long userId = requireUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(401, "账号不存在或已被禁用");
        }
        return user;
    }

    @Override
    public void logout() {
        Long userId = requireUserId();
        redisTemplate.delete(TOKEN_KEY + userId);
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        Long userId = requireUserId();
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException(400, "请输入原密码");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "账号不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }
        validatePassword(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
        // 改密后旧 Token 立即失效，所有端需重新登录
        redisTemplate.delete(TOKEN_KEY + userId);
        log.info("用户 {} 修改密码成功，已清除登录态", user.getUsername());
    }

    @Override
    public void forgotPassword(String phone, String code, String newPassword) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(400, "请输入手机号");
        }
        validatePassword(newPassword);
        verifyCode("forgot", phone, code);

        List<SysUser> users = sysUserMapper.selectList(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, phone).last("LIMIT 1"));
        if (users.isEmpty()) {
            throw new BusinessException(400, "该手机号未绑定账号");
        }
        SysUser user = users.get(0);
        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
        redisTemplate.delete(TOKEN_KEY + user.getId());
        log.info("用户 {} 通过手机验证码重置密码，已清除登录态", user.getUsername());
    }

    @Override
    public void sendSmsCode(String phone, String type) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(400, "请输入手机号");
        }
        if (!"register".equals(type) && !"forgot".equals(type)) {
            throw new BusinessException(400, "验证码类型只能为 register 或 forgot");
        }
        if (!phone.matches("^1\\d{10}$")) {
            throw new BusinessException(400, "手机号格式不正确");
        }
        // 找回密码场景提前校验手机号是否已绑定，避免发无效验证码
        if ("forgot".equals(type)) {
            Long bound = sysUserMapper.selectCount(
                    Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, phone));
            if (bound == null || bound == 0) {
                throw new BusinessException(400, "该手机号未绑定账号");
            }
        }
        // 同一手机号 + 场景 60 秒内只能发一次
        String limitKey = SMS_LIMIT_KEY + type + ":" + phone;
        boolean limited = redisTemplate.opsForValue().get(limitKey) != null;
        if (limited) {
            throw new BusinessException(400, "验证码发送过于频繁，请 1 分钟后重试");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(SMS_CODE_KEY + type + ":" + phone, code, SMS_TTL);
        redisTemplate.opsForValue().set(limitKey, "1", SMS_LIMIT_TTL);

        // 开发/演示环境模拟短信：验证码只打印到后端日志，便于无短信网关时演示
        // 生产环境应改为调用短信服务商接口，绝不能把验证码写进日志或响应体
        log.info("【模拟短信】手机号 {} 的 {} 验证码：{}（5 分钟内有效）", phone, type, code);
    }

    // ================= 私有工具 =================

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    /** 登录成功后写入 Redis 登录态，TTL 与 JWT 有效期保持一致 */
    private void cacheToken(Long userId, String token) {
        redisTemplate.opsForValue().set(TOKEN_KEY + userId, token,
                Duration.ofHours(jwtUtil.getExpireHours()));
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(400, "请输入新密码");
        }
        if (password.length() < 6 || password.length() > 20) {
            throw new BusinessException(400, "密码长度 6-20 位");
        }
    }

    /** 校验并消费验证码（成功即删除，防止重复使用） */
    private void verifyCode(String type, String phone, String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(400, "请输入验证码");
        }
        String key = SMS_CODE_KEY + type + ":" + phone;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            throw new BusinessException(400, "验证码已过期，请重新获取");
        }
        if (!cached.equals(code.trim())) {
            throw new BusinessException(400, "验证码不正确");
        }
        redisTemplate.delete(key);
    }
}
