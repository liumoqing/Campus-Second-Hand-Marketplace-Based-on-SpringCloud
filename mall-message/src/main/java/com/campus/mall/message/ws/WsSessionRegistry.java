package com.campus.mall.message.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话表：一个用户可能有多个端（手机 + 电脑），所以是 userId → Set&lt;session&gt;。
 *
 * <p>推送不保证送达（对方可能离线），**消息一律先落库**，离线用户上线后靠 M3 拉增量，
 * 在线状态只用于把"未读"变成"即时到达"。
 */
@Slf4j
@Component
public class WsSessionRegistry {

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void remove(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    public boolean online(Long userId) {
        Set<WebSocketSession> set = sessions.get(userId);
        return set != null && !set.isEmpty();
    }

    /** 给某个用户的所有端推一帧，返回是否至少送到一处 */
    public boolean send(Long userId, Object frame) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) {
            return false;
        }
        String json;
        try {
            json = mapper.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("帧序列化失败：{}", e.getMessage());
            return false;
        }
        boolean sent = false;
        for (WebSocketSession s : set) {
            if (s != null && s.isOpen()) {
                try {
                    synchronized (s) {
                        s.sendMessage(new TextMessage(json));
                    }
                    sent = true;
                } catch (IOException e) {
                    log.debug("推送失败，稍后靠拉取补齐：{}", e.getMessage());
                }
            }
        }
        return sent;
    }

    /** 踢下线：推 KICKED 后关闭所有连接 */
    public void kick(Long userId, Object frame) {
        Set<WebSocketSession> set = sessions.remove(userId);
        if (set == null) {
            return;
        }
        send0(set, frame);
        for (WebSocketSession s : set) {
            try {
                s.close();
            } catch (IOException ignored) {
                // 关不掉就算了，会话已被移除
            }
        }
    }

    private void send0(Set<WebSocketSession> set, Object frame) {
        String json;
        try {
            json = mapper.writeValueAsString(frame);
        } catch (Exception e) {
            return;
        }
        for (WebSocketSession s : set) {
            if (s != null && s.isOpen()) {
                try {
                    synchronized (s) {
                        s.sendMessage(new TextMessage(json));
                    }
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }
}
