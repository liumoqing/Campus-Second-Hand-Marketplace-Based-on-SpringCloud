package com.campus.mall.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 下游服务不重复做网关的 Bearer 认证：网关已完成 JWT 验签，
 * 这里仅关闭 Spring Security 默认登录页/HTTP Basic，避免内部 Feign 被误拦；
 * 方法级权限仍由 UserContextFilter + @PreAuthorize 执行。
 *
 * mall-auth 需要自己的 Authorization Server 过滤链，因此通过配置关闭本链。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnProperty(name = "mall.security.downstream-enabled", havingValue = "true", matchIfMissing = true)
public class ServiceSecurityConfiguration {

    @Bean
    @Order(0)
    public SecurityFilterChain downstreamSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}