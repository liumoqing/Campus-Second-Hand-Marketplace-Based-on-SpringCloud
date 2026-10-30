package com.campus.mall.message.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/** WebSocket 握手只负责从 OAuth2 JWT 中取 userId；签名、有效期由 JWKS 解码器校验。 */
@Component
public class JwtUtil {

    private final JwtDecoder decoder;

    public JwtUtil(@Value("${mall.security.jwk-set-uri:http://127.0.0.1:4400/oauth2/jwks}") String jwkSetUri) {
        this.decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    /** 解析失败（过期/签名不对/不是 access 令牌）一律返回 null，由拦截器拒绝握手。 */
    public Long userIdOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Jwt jwt = decoder.decode(token);
            String type = jwt.getClaimAsString("type");
            if (type != null && !"access".equals(type)) {
                return null;
            }
            Object uid = jwt.getClaim("uid");
            return uid == null ? null : Long.valueOf(String.valueOf(uid));
        } catch (Exception e) {
            return null;
        }
    }
}