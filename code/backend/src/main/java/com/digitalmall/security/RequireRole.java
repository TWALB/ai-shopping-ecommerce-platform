package com.digitalmall.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解：标注在 Controller 方法或类上，由 JwtInterceptor 校验
 * value 为允许访问的角色（0普通用户 1商家 2管理员），默认全部登录用户
 * 未标注的接口视为公开接口（无需登录）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    int[] value() default {0, 1, 2};

    /** 角色常量 */
    int USER = 0;
    int MERCHANT = 1;
    int ADMIN = 2;
}
