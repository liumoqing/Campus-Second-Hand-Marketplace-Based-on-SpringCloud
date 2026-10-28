package com.campus.mall.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色要求。不写 = 登录即可；写 value = 必须是其中之一。
 * 网关已经拦了后台路径，这里做二次校验（防止绕过网关直连服务端口）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    String[] value() default {};
}
