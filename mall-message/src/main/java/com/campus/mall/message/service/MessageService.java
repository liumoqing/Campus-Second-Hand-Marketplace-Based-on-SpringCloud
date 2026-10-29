package com.campus.mall.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.message.client.UserClient;
import com.campus.mall.message.dto.MessageDtos;
import com.campus.mall.message.entity.Conversation;
import com.campus.mall.message.entity.Message;
import com.campus.mall.message.mapper.ConversationMapper;
import com.campus.mall.message.mapper.MessageMapper;
import com.campus.mall.message.ws.WsSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 聊天消息：发（HTTP 兜底 + WS 共用同一套校验）、拉历史、已读、订单卡片。
 *
 * <p>幂等：`uk_client_msg(from_id, client_msg_id)`——同一个 clientMsgId 重发当"已写"返回，
 * 卡片则用固定 `CARD:{orderNo}:{cardType}`，MQ 重投也不会写两条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 本站上传后的图片地址前缀（mall-product 的 /files/**），防客户端塞外链 */
    private static final String IMAGE_PREFIX = "/files/";

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationService conversationService;
    private final UserClient userClient;
    private final StringRedisTemplate redis;
    private final WsSessionRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    /** M4 / WS CHAT 共用的发送逻辑；返回落库后的消息视图 */
    @Transactional
    public Map<String, Object> send(Long userId, MessageDtos.Send req) {
        Conversation conv = conversationService.requireMember(req.getConversationId(), userId);
        Long peerId = conversationService.peerOf(conv, userId);
        // 3. 收件人必须是会话里那个人，不能借会话发给第三方
        if (!peerId.equals(req.getToId())) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (userId.equals(req.getToId())) {
            throw new BizException(ErrorCode.MESSAGE_SELF);
        }
        // 4. 类型：只允许 TEXT / IMAGE（PRODUCT / ORDER 只能由服务端产生）
        String type = req.getContentType() == null ? "TEXT" : req.getContentType().toUpperCase();
        if (!"TEXT".equals(type) && !"IMAGE".equals(type)) {
            throw new BizException(ErrorCode.MESSAGE_TYPE_UNSUPPORTED);
        }
        // 5. 内容
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if ("TEXT".equals(type)) {
            if (content.isEmpty() || content.length() > 500) {
                throw new BizException(ErrorCode.MESSAGE_CONTENT_INVALID);
            }
        } else {
            if (!content.contains(IMAGE_PREFIX)) {
                throw new BizException(ErrorCode.MESSAGE_CONTENT_INVALID, "图片必须先上传");
            }
        }
        // 5'. 收件人是否被封禁（封了还能看历史，但不能继续收发）
        if (banned(req.getToId())) {
            throw new BizException(ErrorCode.MESSAGE_PEER_BANNED);
        }
        // 6. 限流：同一用户 1 秒 3 条（SET NX EX 建窗口，避免 expire(...) 在 SDR 4.x 自递归）
        String rateKey = "mall:msg:rate:" + userId;
        Boolean first = redis.opsForValue().setIfAbsent(rateKey, "1", Duration.ofSeconds(1));
        Long count = Boolean.TRUE.equals(first) ? 1L : redis.opsForValue().increment(rateKey);
        if (count != null && count > 3) {
            throw new BizException(ErrorCode.MESSAGE_TOO_FREQUENT);
        }

        LocalDateTime now = LocalDateTime.now();
        String clientMsgId = (req.getClientMsgId() == null || req.getClientMsgId().isBlank())
                ? "HTTP:" + System.nanoTime() : req.getClientMsgId();
        Message m = new Message();
        m.setConversationId(conv.getId());
        m.setFromId(userId);
        m.setToId(req.getToId());
        m.setContentType(type);
        m.setContent(content);
        m.setStatus("SENT");
        m.setClientMsgId(clientMsgId);
        m.setCreateTime(now);
        m.setUpdateTime(now);
        try {
            messageMapper.insert(m);
        } catch (DuplicateKeyException e) {
            // 同一条消息重发（WS 重连/HTTP 重试）：查出已有的那条返回
            Message exists = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getFromId, userId).eq(Message::getClientMsgId, clientMsgId).last("LIMIT 1"));
            if (exists == null) {
                throw new BizException(ErrorCode.MESSAGE_SEND_FAILED);
            }
            return vo(exists);
        }

        String summary = "IMAGE".equals(type) ? "[图片]" : content;
        conversationService.touch(conv, summary, type, null, req.getToId(), now);

        Map<String, Object> vo = vo(m);
        // 送达方在线才推；离线靠 M3 拉取（消息已落库，不会丢）
        registry.send(req.getToId(), frame("CHAT", null, vo));
        return vo;
    }

    /** M3 历史：首屏 + 游标上拉 */
    public Map<String, Object> history(Long userId, Long conversationId, Long lastId, int size) {
        conversationService.requireMember(conversationId, userId);
        int limit = Math.min(Math.max(1, size), 50);
        List<Message> rows = (lastId == null)
                ? messageMapper.historyFirst(conversationId, limit + 1)
                : messageMapper.historyBefore(conversationId, lastId, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Message m : rows) {
            records.add(vo(m));
        }
        Long next = rows.isEmpty() ? null : rows.get(rows.size() - 1).getId();
        Map<String, Object> out = new HashMap<>();
        out.put("records", records);
        out.put("hasMore", hasMore);
        out.put("lastId", next == null ? null : String.valueOf(next));
        return out;
    }

    /** 聊天侧已读：把 upTo 之前的对方消息置 READ，并清掉该会话未读 */
    public long markChatRead(Long userId, Long conversationId, Long lastMsgId) {
        Conversation conv = conversationService.requireMember(conversationId, userId);
        LocalDateTime now = LocalDateTime.now();
        Long upTo = lastMsgId;
        if (upTo == null) {
            Message latest = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conversationId).orderByDesc(Message::getId).last("LIMIT 1"));
            upTo = latest == null ? 0L : latest.getId();
        }
        messageMapper.markReadUpTo(conversationId, userId, upTo, now);
        conversationService.clearUnread(conv, userId, now);
        // 让对方的"已读回执"即时生效
        registry.send(conversationService.peerOf(conv, userId), frame("READ", null,
                Map.of("conversationId", String.valueOf(conversationId), "readerId", String.valueOf(userId))));
        return conversationService.unreadTotal(userId);
    }

    /** M13：订单卡片（mall-order 调）。会话不存在就先建（买卖双方 + 商品），保证卡片一定有地方落 */
    @Transactional
    public void card(MessageDtos.Card req) {
        Long buyer = req.getBuyerId();
        Long seller = req.getSellerId();
        long a = Math.min(buyer, seller);
        long b = Math.max(buyer, seller);
        Conversation conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserAId, a).eq(Conversation::getUserBId, b)
                .eq(Conversation::getProductId, req.getProductId()).last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (conv == null) {
            conv = new Conversation();
            conv.setUserAId(a);
            conv.setUserBId(b);
            conv.setProductId(req.getProductId());
            conv.setLastMsg("");
            conv.setLastMsgType("TEXT");
            conv.setLastTime(now);
            conv.setUnreadA(0);
            conv.setUnreadB(0);
            conv.setCreateTime(now);
            conv.setUpdateTime(now);
            try {
                conversationMapper.insert(conv);
            } catch (DuplicateKeyException e) {
                conv = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserAId, a).eq(Conversation::getUserBId, b)
                        .eq(Conversation::getProductId, req.getProductId()).last("LIMIT 1"));
            }
        }
        if (conv == null) {
            log.warn("订单卡片建会话失败：orderNo={}", req.getOrderNo());
            return;
        }

        // 卡片内容由服务端拼，客户端永远伪造不了
        Map<String, Object> card = new HashMap<>();
        card.put("kind", "ORDER");
        card.put("orderNo", req.getOrderNo());
        card.put("cardType", req.getCardType());
        card.put("productId", String.valueOf(req.getProductId()));
        card.put("productTitle", req.getProductTitle());
        card.put("productCover", req.getProductCover());
        card.put("statusText", req.getStatusText());
        card.put("actionText", req.getActionText());
        String content;
        try {
            content = mapper.writeValueAsString(card);
        } catch (Exception e) {
            content = "{\"kind\":\"ORDER\",\"orderNo\":\"" + req.getOrderNo() + "\"}";
        }
        String summary = req.getStatusText() == null ? "订单更新" : req.getStatusText();

        Message m = new Message();
        m.setConversationId(conv.getId());
        m.setFromId(sellerOf(req));
        m.setToId(buyerOf(req));
        m.setContentType("ORDER");
        m.setCardType(req.getCardType());
        m.setContent(content);
        m.setStatus("SENT");
        m.setClientMsgId("CARD:" + req.getOrderNo() + ":" + req.getCardType());
        m.setCreateTime(now);
        m.setUpdateTime(now);
        try {
            messageMapper.insert(m);
        } catch (DuplicateKeyException e) {
            // MQ 重投 / 兜底再调：这张卡已经写过了，直接返回
            log.debug("订单卡片已存在：{} {}", req.getOrderNo(), req.getCardType());
            return;
        }
        conversationService.touch(conv, summary, "ORDER", req.getCardType(), m.getToId(), now);
        registry.send(m.getToId(), frame("CARD", null, vo(m)));
    }

    /** 卡片方向：订单类卡片默认由"发起方"发给"对方"，卖家的动作类（发货）由卖家发 */
    private Long sellerOf(MessageDtos.Card req) {
        return isSellerSide(req.getCardType()) ? req.getSellerId() : req.getBuyerId();
    }

    private Long buyerOf(MessageDtos.Card req) {
        return isSellerSide(req.getCardType()) ? req.getBuyerId() : req.getSellerId();
    }

    /** ORDER_SHIPPED 是卖家对买家的动作，其余是买家的进展通知卖家 */
    private boolean isSellerSide(String cardType) {
        return "ORDER_SHIPPED".equals(cardType) || "REFUND_DONE".equals(cardType);
    }

    public Map<String, Object> vo(Message m) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("msgId", String.valueOf(m.getId()));
        vo.put("conversationId", String.valueOf(m.getConversationId()));
        vo.put("fromId", String.valueOf(m.getFromId()));
        vo.put("toId", String.valueOf(m.getToId()));
        vo.put("contentType", m.getContentType());
        vo.put("cardType", m.getCardType());
        vo.put("content", parseContent(m));
        vo.put("status", m.getStatus());
        vo.put("clientMsgId", m.getClientMsgId());
        vo.put("createTime", m.getCreateTime() == null ? null : FMT.format(m.getCreateTime()));
        return vo;
    }

    /** TEXT/IMAGE 的 content 是字符串；PRODUCT/ORDER 是对象（判断永远看 contentType） */
    private Object parseContent(Message m) {
        String type = m.getContentType();
        if ("PRODUCT".equals(type) || "ORDER".equals(type)) {
            try {
                return mapper.readValue(m.getContent(), Map.class);
            } catch (Exception e) {
                return m.getContent();
            }
        }
        return m.getContent();
    }

    private Map<String, Object> frame(String type, String code, Object data) {
        Map<String, Object> f = new HashMap<>();
        f.put("type", type);
        if (code != null) {
            f.put("code", code);
        }
        if (data != null) {
            f.put("data", data);
        }
        return f;
    }

    private boolean banned(Long userId) {
        try {
            var r = userClient.brief(userId);
            if (r != null && r.getCode() == 0 && r.getData() != null) {
                return MallConstants.STATUS_BANNED.equals(String.valueOf(r.getData().get("status")));
            }
        } catch (Exception e) {
            log.debug("查封禁状态失败（按未封禁处理）：{}", e.getMessage());
        }
        return false;
    }
}
