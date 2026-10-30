package com.campus.mall.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** 开启 @PreAuthorize；各服务通过网关注入的身份头建立 Authentication。 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class MethodSecurityConfiguration {
}