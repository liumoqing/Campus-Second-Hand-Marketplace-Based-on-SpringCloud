package com.campus.mall.common.security;

import com.campus.mall.common.constant.MallConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把网关注入的 X-User-Id / X-User-Role 放进 {@link UserContext}，请求结束后清理。
 * 直接访问服务端口时（本地调试）也可以手动带这两个头。
 */
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uid = request.getHeader(MallConstants.HEADER_USER_ID);
        String role = request.getHeader(MallConstants.HEADER_USER_ROLE);
        try {
            if (uid != null && !uid.isBlank()) {
                UserContext.set(Long.parseLong(uid), role == null ? MallConstants.ROLE_USER : role);
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
