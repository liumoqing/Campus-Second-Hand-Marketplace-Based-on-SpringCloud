package com.campus.mall.message.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手时把 `?token=` 里的 accessToken 换成 userId 塞进 attributes。
 *
 * <p>失败直接拒绝握手（返回 false），前端拿到的是 HTTP 403/401，按"登录失效"处理即可。
 */
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = null;
        if (request instanceof ServletServerHttpRequest servlet) {
            token = servlet.getServletRequest().getParameter("token");
        }
        if (token == null && request.getURI().getQuery() != null) {
            for (String kv : request.getURI().getQuery().split("&")) {
                if (kv.startsWith("token=")) {
                    token = kv.substring("token=".length());
                    break;
                }
            }
        }
        Long userId = jwtUtil.userIdOf(token);
        if (userId == null) {
            return false;
        }
        attributes.put(ATTR_USER_ID, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成无需处理
    }
}
