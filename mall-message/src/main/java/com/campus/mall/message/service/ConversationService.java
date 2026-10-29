package com.campus.mall.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.message.client.ProductClient;
import com.campus.mall.message.client.UserClient;
import com.campus.mall.message.dto.MessageDtos;
import com.campus.mall.message.entity.Conversation;
import com.campus.mall.message.entity.Message;
import com.campus.mall.message.mapper.ConversationMapper;
import com.campus.mall.message.mapper.MessageMapper;
import com.campus.mall.message.ws.WsSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话：一对用户 + 一个商品 = 一条会话（uk_conv 保证唯一）。
 *
 * <p>未读数以 Redis Hash `mall:msg:unread:{userId}` 为权威（响应体里的红点直接读它），
 * 数据库的 unread_a/unread_b 只用于重建；两者由 touch() 同时维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final StringRedisTemplate redis;
    private final WsSessionRegistry registry;

    /** M12：有则返回，无则创建；首次且 openWithProductCard 时写一条 PRODUCT 卡片开场 */
    @Transactional
    public Map<String, Object> getOrCreate(Long userId, MessageDtos.CreateConversation req) {
        Long peerId = req.getPeerId();
        if (peerId.equals(userId)) {
            throw new BizException(ErrorCode.MESSAGE_SELF);
        }
        Map<String, Object> peer = safeBrief(peerId);
        if (peer == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该用户不存在");
        }
        if (MallConstants.STATUS_BANNED.equals(String.valueOf(peer.get("status")))) {
            throw new BizException(ErrorCode.MESSAGE_PEER_BANNED);
        }
        Map<String, Object> product = safeProduct(req.getProductId());
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        long a = Math.min(userId, peerId);
        long b = Math.max(userId, peerId);
        Conversation exists = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserAId, a).eq(Conversation::getUserBId, b)
                .eq(Conversation::getProductId, req.getProductId()).last("LIMIT 1"));
        if (exists != null) {
            return Map.of("conversationId", exists.getId(), "created", false);
        }

        LocalDateTime now = LocalDateTime.now();
        Conversation conv = new Conversation();
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
            // 并发下同一对用户正在建同一个会话，唯一键兜住，回查即可
            Conversation again = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                    .eq(Conversation::getUserAId, a).eq(Conversation::getUserBId, b)
                    .eq(Conversation::getProductId, req.getProductId()).last("LIMIT 1"));
            return Map.of("conversationId", again.getId(), "created", false);
        }

        boolean openWithCard = req.getOpenWithProductCard() == null || req.getOpenWithProductCard();
        if (openWithCard) {
            insertProductCard(conv, userId, peerId, product, now);
        }
        return Map.of("conversationId", conv.getId(), "created", true);
    }

    /** M2：我参与的会话，按 last_time 倒序；keyword 只做内存过滤（total 是过滤后条数） */
    public PageResult<Map<String, Object>> list(Long userId, int pageNum, int pageSize, String keyword) {
        int page = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), 50);
        int offset = (page - 1) * size;
        List<Conversation> rows = conversationMapper.pageOfUser(userId, offset, size);
        long total = conversationMapper.countOfUser(userId);

        // 批量补对方昵称头像 + 商品封面，避免 N+1
        List<Long> peerIds = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        for (Conversation c : rows) {
            peerIds.add(peerOf(c, userId));
            productIds.add(c.getProductId());
        }
        Map<Long, Map<String, Object>> peers = safeBatchUsers(peerIds);
        Map<Long, Map<String, Object>> products = safeBatchProducts(productIds);

        List<Map<String, Object>> records = new ArrayList<>();
        for (Conversation c : rows) {
            Long peerId = peerOf(c, userId);
            Map<String, Object> peer = peers.getOrDefault(peerId, Map.of());
            String nickname = String.valueOf(peer.getOrDefault("nickname", "用户" + peerId));
            String summary = summaryOf(c);
            if (keyword != null && !keyword.isBlank()
                    && !nickname.contains(keyword) && !summary.contains(keyword)) {
                continue;
            }
            Map<String, Object> vo = new HashMap<>();
            vo.put("conversationId", String.valueOf(c.getId()));
            vo.put("peerId", String.valueOf(peerId));
            vo.put("peerNickname", nickname);
            vo.put("peerAvatar", peer.get("avatar"));
            vo.put("peerOnline", registry.online(peerId));
            vo.put("productId", String.valueOf(c.getProductId()));
            vo.put("productTitle", products.getOrDefault(c.getProductId(), Map.of()).get("title"));
            vo.put("productCover", products.getOrDefault(c.getProductId(), Map.of()).get("coverUrl"));
            vo.put("lastMsg", c.getLastMsg());
            vo.put("lastMsgType", c.getLastMsgType());
            vo.put("lastMsgCardType", c.getLastCardType());
            vo.put("lastTime", c.getLastTime() == null ? null : FMT.format(c.getLastTime()));
            vo.put("unreadCount", unreadOf(c, userId));
            records.add(vo);
        }
        // 有 keyword 时是内存过滤，total 只能给"本页命中条数"，03 §7 M2 已注明
        long hit = (keyword == null || keyword.isBlank()) ? total : records.size();
        return PageResult.from(records, hit, page, size);
    }

    /** 会话成员校验：不是你的会话 → 60001 */
    public Conversation requireMember(Long conversationId, Long userId) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null || (!userId.equals(c.getUserAId()) && !userId.equals(c.getUserBId()))) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return c;
    }

    public Long peerOf(Conversation c, Long userId) {
        return userId.equals(c.getUserAId()) ? c.getUserBId() : c.getUserAId();
    }

    /** 读并更新会话摘要（由 MessageService 在消息落库后调用）：未读加在**收件人**身上 */
    public void touch(Conversation c, String summary, String msgType, String cardType, Long toId, LocalDateTime now) {
        conversationMapper.touch(c.getId(), summary, msgType, cardType, toId, now);
        redis.opsForHash().increment(MallConstants.CACHE_MSG_UNREAD + toId, String.valueOf(c.getId()), 1);
    }

    /** 清一个会话的未读（Redis Hash 该 field 删掉 + DB 计数归零） */
    public void clearUnread(Conversation c, Long userId, LocalDateTime now) {
        conversationMapper.clearUnread(c.getId(), userId, now);
        redis.opsForHash().delete(MallConstants.CACHE_MSG_UNREAD + userId, String.valueOf(c.getId()));
    }

    /** 全局未读总数（红点）：Redis Hash 求和，Redis 丢了就按 DB 汇总兜底 */
    public long unreadTotal(Long userId) {
        Map<Object, Object> hash = redis.opsForHash().entries(MallConstants.CACHE_MSG_UNREAD + userId);
        if (hash != null && !hash.isEmpty()) {
            long sum = 0;
            for (Object v : hash.values()) {
                try {
                    sum += Long.parseLong(String.valueOf(v));
                } catch (NumberFormatException ignored) {
                    // 脏值忽略
                }
            }
            return sum;
        }
        return 0;
    }

    public int unreadOf(Conversation c, Long userId) {
        Integer v = userId.equals(c.getUserAId()) ? c.getUnreadA() : c.getUnreadB();
        return v == null ? 0 : v;
    }

    private String summaryOf(Conversation c) {
        String type = c.getLastMsgType();
        if (c.getLastTime() == null || c.getLastMsg() == null) {
            return "开始聊聊吧";
        }
        if ("ORDER".equals(type)) {
            return "[订单] " + c.getLastMsg();
        }
        if ("PRODUCT".equals(type)) {
            return "[商品] " + c.getLastMsg();
        }
        if ("IMAGE".equals(type)) {
            return "[图片]";
        }
        return c.getLastMsg();
    }

    /** 建会话时写一条 PRODUCT 卡片，作为"关于这件商品"的上下文 */
    private void insertProductCard(Conversation conv, Long fromId, Long toId, Map<String, Object> product, LocalDateTime now) {
        String title = String.valueOf(product.getOrDefault("title", "商品"));
        Map<String, Object> card = new HashMap<>();
        card.put("kind", "PRODUCT");
        card.put("productId", String.valueOf(conv.getProductId()));
        card.put("title", title);
        card.put("coverUrl", product.get("coverUrl"));
        card.put("price", product.get("price"));
        card.put("status", product.get("status"));
        String content;
        try {
            content = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(card);
        } catch (Exception e) {
            content = "{\"kind\":\"PRODUCT\"}";
        }
        Message m = new Message();
        m.setConversationId(conv.getId());
        m.setFromId(fromId);
        m.setToId(toId);
        m.setContentType("PRODUCT");
        m.setContent(content);
        m.setStatus("SENT");
        m.setClientMsgId("PRODUCT:" + conv.getProductId() + ":" + conv.getId());
        m.setCreateTime(now);
        m.setUpdateTime(now);
        try {
            messageMapper.insert(m);
            conversationMapper.touch(conv.getId(), title, "PRODUCT", null, toId, now);
        } catch (DuplicateKeyException ignored) {
            // 并发建会话时这条开场卡片可能已经写过，忽略
        }
    }

    private Map<String, Object> safeBrief(Long userId) {
        try {
            var r = userClient.brief(userId);
            return r != null && r.getCode() == 0 ? r.getData() : null;
        } catch (Exception e) {
            log.debug("取用户 {} 失败：{}", userId, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> safeProduct(Long productId) {
        try {
            var r = productClient.view(productId);
            return r != null && r.getCode() == 0 ? r.getData() : null;
        } catch (Exception e) {
            log.debug("取商品 {} 失败：{}", productId, e.getMessage());
            return null;
        }
    }

    private Map<Long, Map<String, Object>> safeBatchUsers(List<Long> ids) {
        Map<Long, Map<String, Object>> map = new HashMap<>();
        try {
            var r = userClient.batch(ids);
            if (r != null && r.getCode() == 0 && r.getData() != null) {
                for (Map<String, Object> u : r.getData()) {
                    map.put(Long.valueOf(String.valueOf(u.get("id"))), u);
                }
            }
        } catch (Exception e) {
            log.debug("批量取用户失败：{}", e.getMessage());
        }
        return map;
    }

    private Map<Long, Map<String, Object>> safeBatchProducts(List<Long> ids) {
        Map<Long, Map<String, Object>> map = new HashMap<>();
        try {
            var r = productClient.batch(ids);
            if (r != null && r.getCode() == 0 && r.getData() != null) {
                for (Map<String, Object> p : r.getData()) {
                    map.put(Long.valueOf(String.valueOf(p.get("id"))), p);
                }
            }
        } catch (Exception e) {
            log.debug("批量取商品失败：{}", e.getMessage());
        }
        return map;
    }
}
