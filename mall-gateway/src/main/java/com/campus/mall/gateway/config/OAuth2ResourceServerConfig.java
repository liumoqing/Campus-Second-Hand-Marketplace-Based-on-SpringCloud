package com.campus.mall.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关资源服务器配置。JWT 由 mall-auth 的 /oauth2/jwks 提供公钥验签；
 * 现有 AuthGlobalFilter 继续负责 token_version、封禁状态和下游用户头注入。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class OAuth2ResourceServerConfig {

    @Bean
    @Order(0)
    public SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http,
                                                                 ObjectMapper objectMapper) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/auth/**", "/oauth2/**", "/.well-known/**", "/login", "/error", "/inner/**",
                                "/product/page", "/category/tree", "/files/**", "/static/**",
                                "/payment/callback/**", "/config/runtime", "/ws/**",
                                "/actuator/**", "/favicon.ico").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${mall.security.jwk-set-uri:http://127.0.0.1:4400/oauth2/jwks}") String jwkSetUri) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /** 供同步 AuthGlobalFilter 使用；避免在 Netty event-loop 上 block ReactiveJwtDecoder。 */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${mall.security.jwk-set-uri:http://127.0.0.1:4400/oauth2/jwks}") String jwkSetUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    private ServerAuthenticationEntryPoint entryPoint(ObjectMapper objectMapper) {
        return (exchange, exception) -> writeError(exchange, objectMapper, HttpStatus.UNAUTHORIZED, 10002,
                "未登录或登录已过期");
    }

    private ServerAccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (exchange, exception) -> writeError(exchange, objectMapper, HttpStatus.FORBIDDEN, 10003,
                "无权限执行该操作");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ObjectMapper objectMapper,
                                  HttpStatus status, int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        body.put("timestamp", System.currentTimeMillis());
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}