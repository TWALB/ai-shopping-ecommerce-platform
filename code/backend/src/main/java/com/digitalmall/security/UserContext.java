package com.digitalmall.security;

/**
 * 当前登录用户上下文（ThreadLocal），由 JwtInterceptor 在请求进入时写入、结束时清理
 */
public class UserContext {

    /** 当前登录用户信息 */
    public record CurrentUser(Long id, String username, Integer role) {
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 获取当前用户ID，未登录返回 null */
    public static Long getUserId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
