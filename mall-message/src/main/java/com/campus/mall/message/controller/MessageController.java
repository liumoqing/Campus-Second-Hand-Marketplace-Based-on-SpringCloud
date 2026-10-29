package com.campus.mall.message.controller;

import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.message.dto.MessageDtos;
import com.campus.mall.message.service.ConversationService;
import com.campus.mall.message.service.MessageService;
import com.campus.mall.message.service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 聊天与通知对前端暴露的接口（M2~M6 / M10 / M12）。 */
@RestController
@RequestMapping("/message")
@RequireRole
@RequiredArgsConstructor
public class MessageController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final NoticeService noticeService;

    /** M2 会话列表 */
    @GetMapping("/conversations")
    public R<PageResult<Map<String, Object>>> conversations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return R.ok(conversationService.list(UserContext.requireUserId(), pageNum, pageSize, keyword));
    }

    /** M3 历史消息（游标上拉） */
    @GetMapping("/history")
    public R<Map<String, Object>> history(@RequestParam Long conversationId,
                                          @RequestParam(required = false) Long lastId,
                                          @RequestParam(defaultValue = "20") int size) {
        return R.ok(messageService.history(UserContext.requireUserId(), conversationId, lastId, size));
    }

    /** M4 发消息（WS 断线兜底，与 WS CHAT 同一套校验） */
    @PostMapping("/send")
    public R<Map<String, Object>> send(@Valid @RequestBody MessageDtos.Send req) {
        return R.ok(messageService.send(UserContext.requireUserId(), req));
    }

    /** M5 已读：聊天 `{conversationId,lastMsgId}` 与通知 `{noticeIds}` 互斥；通知传空数组表示全部已读 */
    @PutMapping("/read")
    public R<Map<String, Object>> read(@RequestBody MessageDtos.Read req) {
        Long userId = UserContext.requireUserId();
        boolean chat = req.getConversationId() != null;
        boolean notice = req.getNoticeIds() != null;
        if (chat == notice) {
            throw new BizException(ErrorCode.PARAM_INVALID, "conversationId 与 noticeIds 必须二选一");
        }
        long unreadTotal;
        if (chat) {
            unreadTotal = messageService.markChatRead(userId, req.getConversationId(), req.getLastMsgId());
        } else {
            noticeService.markRead(userId, req.getNoticeIds());
            unreadTotal = conversationService.unreadTotal(userId);
        }
        return R.ok(Map.of("unreadTotal", unreadTotal));
    }

    /** M6 系统通知列表 */
    @GetMapping("/notices")
    public R<PageResult<Map<String, Object>>> notices(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) Boolean unreadOnly) {
        return R.ok(noticeService.page(UserContext.requireUserId(), pageNum, pageSize, type, unreadOnly));
    }

    /** M10 通知未读数（铃铛红点） */
    @GetMapping("/notices/unread-count")
    public R<Map<String, Object>> noticeUnreadCount() {
        long total = noticeService.unreadCount(UserContext.requireUserId());
        return R.ok(Map.of("total", total));
    }

    /** M12 建/取会话（联系卖家） */
    @PostMapping("/conversation")
    public R<Map<String, Object>> createConversation(@Valid @RequestBody MessageDtos.CreateConversation req) {
        return R.ok(conversationService.getOrCreate(UserContext.requireUserId(), req));
    }
}
