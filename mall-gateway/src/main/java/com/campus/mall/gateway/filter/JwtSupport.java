package com.campus.mall.gateway.filter;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

/**
 * 网关侧 JWT 解析：使用 OAuth2 Resource Server 的 JWK 解码器验证签名与有效期，
 * 不生成 token（生成在 mall-auth）。
 */
@Component
public class JwtSupport {

    private final JwtDecoder decoder;

    public JwtSupport(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * @return JWT claims；签名不对/过期/解码失败返回 null
     */
    public Jwt parse(String token) {
        try {
            return decoder.decode(token);
        } catch (Exception e) {
            return null;
        }
    }
}