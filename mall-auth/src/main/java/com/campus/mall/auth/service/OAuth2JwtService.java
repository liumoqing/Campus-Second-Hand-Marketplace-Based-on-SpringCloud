package com.campus.mall.auth.service;

import com.campus.mall.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** 使用 OAuth2 授权服务器的 RSA JwtEncoder 签发兼容旧登录接口的 access token。 */
@Service
@RequiredArgsConstructor
public class OAuth2JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtUtil jwtUtil;

    @Value("${mall.oauth2.issuer:http://127.0.0.1:4400}")
    private String issuer;

    public String accessToken(Long userId, String role, int tokenVersion) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtUtil.accessSeconds()))
                .claim("jti", UUID.randomUUID().toString())
                .claim("uid", userId)
                .claim("role", role)
                .claim("tv", tokenVersion)
                .claim("type", "access")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }
}