package com.campus.mall.common.security;

import com.campus.mall.common.constant.MallConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 把网关注入的 X-User-Id / X-User-Role 放进 {@link UserContext}，
 * 同时建立 Spring Security Authentication，供 @PreAuthorize 使用方法级权限。
 */
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uid = request.getHeader(MallConstants.HEADER_USER_ID);
        String role = request.getHeader(MallConstants.HEADER_USER_ROLE);
        try {
            if (uid != null && !uid.isBlank()) {
                Long userId = Long.parseLong(uid);
                String actualRole = role == null ? MallConstants.ROLE_USER : role;
                UserContext.set(userId, actualRole);
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + actualRole)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
