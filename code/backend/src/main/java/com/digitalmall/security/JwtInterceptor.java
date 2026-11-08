package com.digitalmall.security;

import com.digitalmall.common.BusinessException;
import com.digitalmall.entity.SysUser;
import com.digitalmall.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录鉴权拦截器：
 * 1. 接口标注 @RequireRole → 校验 Token + 角色权限
 * 2. 未标注 → 公开接口直接放行
 * 3. 每次请求实时校验用户状态与角色（禁用即失效、调整角色即时生效）
 * 4. 每次请求比对 Redis 中的登录态：退出登录 / 修改密码 / 重置密码后旧 Token 立即失效
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String TOKEN_KEY = "login:token:";

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate redisTemplate;

    public JwtInterceptor(JwtUtil jwtUtil, SysUserMapper sysUserMapper,
                          StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 读取方法级或类级 @RequireRole
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return true; // 公开接口
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        try {
            String token = auth.substring(7);
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());

            // 登录态校验：登出 / 改密 / 重置密码会删除该 Key，旧 Token 立即失效
            String cached;
            try {
                cached = redisTemplate.opsForValue().get(TOKEN_KEY + userId);
            } catch (Exception e) {
                throw new BusinessException(503, "登录状态校验失败，请确认 Redis 已启动");
            }
            if (cached == null || !cached.equals(token)) {
                throw new BusinessException(401, "登录已失效，请重新登录");
            }

            // 实时校验用户状态与角色（管理员禁用/改角色即时生效）
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(401, "账号不存在");
            }
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new BusinessException(401, "账号已被禁用");
            }
            UserContext.set(new UserContext.CurrentUser(user.getId(), user.getUsername(), user.getRole()));

            // 角色校验（以数据库实时角色为准）
            boolean allowed = false;
            for (int r : requireRole.value()) {
                if (r == user.getRole()) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new BusinessException(403, "无权访问该资源");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(401, "Token无效或已过期");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
