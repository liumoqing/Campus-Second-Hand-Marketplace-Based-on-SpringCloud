package com.campus.mall.message.ws;

import com.campus.mall.common.exception.BizException;
import com.campus.mall.message.dto.MessageDtos;
import com.campus.mall.message.service.MessageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天 WebSocket：上行 CHAT / READ / PING，下行 ACK / CHAT / CARD / READ / PONG / ERROR / NOTICE / KICKED。
 *
 * <p>字段名与 HTTP 完全一致（`toId` 不是 `toUserId`、回执叫 `ACK`、时间为 `yyyy-MM-dd HH:mm:ss` 字符串）。
 * 一条消息"落库成功"才算发出，ACK 里带 msgId 与 createTime，前端拿它替换本地临时气泡。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MessageService messageService;
    private final WsSessionRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        if (userId == null) {
            close(session, CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        registry.add(userId, session);
        log.debug("WS 连接建立 userId={} sessionId={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId != null) {
            registry.remove(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = userId(session);
        if (userId == null) {
            close(session, CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, "10001", "帧格式不合法");
            return;
        }
        String type = node.path("type").asText("");
        try {
            switch (type) {
                case "PING" -> send(session, Map.of("type", "PONG"));
                case "CHAT" -> handleChat(session, userId, node);
                case "READ" -> handleRead(session, userId, node);
                default -> sendError(session, "10001", "未知帧类型");
            }
        } catch (BizException e) {
            sendError(session, String.valueOf(e.getCode()), e.getMessage());
        } catch (Exception e) {
            log.warn("WS 处理失败 userId={} type={}：{}", userId, type, e.getMessage());
            sendError(session, "10007", "服务异常");
        }
    }

    private void handleChat(WebSocketSession session, Long userId, JsonNode node) {
        MessageDtos.Send req = new MessageDtos.Send();
        req.setConversationId(longOrNull(node, "conversationId"));
        req.setToId(longOrNull(node, "toId"));
        req.setContentType(node.path("contentType").asText("TEXT"));
        req.setContent(node.path("content").asText(""));
        String clientMsgId = node.path("clientMsgId").asText(null);
        req.setClientMsgId(clientMsgId);
        if (req.getConversationId() == null || req.getToId() == null) {
            sendError(session, "10001", "conversationId 与 toId 不能为空");
            return;
        }
        Map<String, Object> vo = messageService.send(userId, req);
        Map<String, Object> ack = new HashMap<>();
        ack.put("clientMsgId", vo.get("clientMsgId"));
        ack.put("msgId", vo.get("msgId"));
        ack.put("createTime", vo.get("createTime"));
        send(session, Map.of("type", "ACK", "data", ack));
    }

    private void handleRead(WebSocketSession session, Long userId, JsonNode node) {
        Long conversationId = longOrNull(node, "conversationId");
        if (conversationId == null) {
            sendError(session, "10001", "conversationId 不能为空");
            return;
        }
        Long lastMsgId = longOrNull(node, "lastMsgId");
        long unreadTotal = messageService.markChatRead(userId, conversationId, lastMsgId);
        send(session, Map.of("type", "READ", "data",
                Map.of("conversationId", String.valueOf(conversationId), "unreadTotal", unreadTotal)));
    }

    private void sendError(WebSocketSession session, String code, String message) {
        send(session, Map.of("type", "ERROR", "code", code, "message", message == null ? "" : message));
    }

    private void send(WebSocketSession session, Object frame) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(frame)));
                }
            }
        } catch (Exception e) {
            log.debug("回写失败：{}", e.getMessage());
        }
    }

    private Long userId(WebSocketSession session) {
        Object v = session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        return v == null ? null : Long.valueOf(String.valueOf(v));
    }

    private Long longOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        try {
            return Long.valueOf(n.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // 关闭失败忽略
        }
    }
}
