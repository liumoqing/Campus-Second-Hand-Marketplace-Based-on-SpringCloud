package com.campus.mall.common.web;

import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.R;
import com.campus.mall.common.constant.MallConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /inner/** 是服务间接口，网关不路由，只允许带 internal-token 的调用进来。
 * 令牌值来自配置 mall.internal.token（各服务一致）。
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!MATCHER.match("/inner/**", request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader(MallConstants.HEADER_INTERNAL_TOKEN);
        if (expectedToken == null || expectedToken.isBlank() || !expectedToken.equals(token)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    R.fail(ErrorCode.INNER_TOKEN_INVALID)));
            response.getWriter().flush();
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/inner/");
    }
}
