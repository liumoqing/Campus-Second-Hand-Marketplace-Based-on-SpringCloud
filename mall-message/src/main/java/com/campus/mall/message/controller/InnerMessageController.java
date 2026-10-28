package com.campus.mall.message.controller;

import com.campus.mall.common.api.R;
import com.campus.mall.message.dto.MessageDtos;
import com.campus.mall.message.service.ConversationService;
import com.campus.mall.message.service.MessageService;
import com.campus.mall.message.service.NoticeService;
import com.campus.mall.message.ws.WsSessionRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部接口（不经网关，统一校验 X-Internal-Token）：M7 未读数 / M11 站内通知 / M13 订单卡片 / 强制下线。
 */
@RestController
@RequestMapping("/inner/message")
@RequiredArgsConstructor
public class InnerMessageController {

    private final ConversationService conversationService;
    private final NoticeService noticeService;
    private final MessageService messageService;
    private final WsSessionRegistry registry;

    /** M7 给 mall-user 用：聊天未读 + 通知未读 */
    @GetMapping("/unread/{userId}")
    public R<Map<String, Object>> unread(@PathVariable Long userId) {
        return R.ok(Map.of(
                "chatUnread", conversationService.unreadTotal(userId),
                "noticeUnread", noticeService.unreadCount(userId)));
    }

    /** M11 站内通知 */
    @PostMapping("/notice")
    public R<Void> notice(@Valid @RequestBody MessageDtos.Notice req) {
        noticeService.create(req);
        return R.ok();
    }

    /** M13 订单状态卡片（mall-order 调） */
    @PostMapping("/card")
    public R<Void> card(@Valid @RequestBody MessageDtos.Card req) {
        messageService.card(req);
        return R.ok();
    }

    /** 封禁即时踢下线（mall-user 调）：先推 KICKED 帧再关连接 */
    @PostMapping("/kick")
    public R<Void> kick(@Valid @RequestBody MessageDtos.Kick req) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("reason", req.getReason() == null ? "BANNED" : req.getReason());
        data.put("banUntil", req.getBanUntil());
        data.put("message", req.getMessage());
        registry.kick(req.getUserId(), Map.of("type", "KICKED", "data", data));
        return R.ok();
    }
}
