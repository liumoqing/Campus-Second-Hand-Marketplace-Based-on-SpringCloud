package com.campus.mall.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.message.dto.MessageDtos;
import com.campus.mall.message.entity.Notice;
import com.campus.mall.message.mapper.NoticeMapper;
import com.campus.mall.message.ws.WsSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 站内通知：M6 列表 / M10 未读数 / M11 内部投递 / M7 未读数（给 mall-user 用）。 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NoticeMapper noticeMapper;
    private final WsSessionRegistry registry;

    /** M6 */
    public PageResult<Map<String, Object>> page(Long userId, int pageNum, int pageSize, String type, Boolean unreadOnly) {
        int page = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), 50);
        LambdaQueryWrapper<Notice> q = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getUserId, userId)
                .eq(type != null && !type.isBlank(), Notice::getType, type)
                .eq(Boolean.TRUE.equals(unreadOnly), Notice::getReadFlag, 0)
                .orderByDesc(Notice::getId);
        Page<Notice> p = noticeMapper.selectPage(new Page<>(page, size), q);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Notice n : p.getRecords()) {
            Map<String, Object> vo = new HashMap<>();
            vo.put("noticeId", String.valueOf(n.getId()));
            vo.put("type", n.getType());
            vo.put("title", n.getTitle());
            vo.put("content", n.getContent());
            vo.put("bizId", n.getBizId());
            vo.put("bizType", n.getBizType());
            vo.put("isRead", Integer.valueOf(1).equals(n.getReadFlag()));
            vo.put("createTime", n.getCreateTime() == null ? null : FMT.format(n.getCreateTime()));
            records.add(vo);
        }
        return PageResult.from(records, p.getTotal(), page, size);
    }

    public long unreadCount(Long userId) {
        return noticeMapper.unreadCount(userId);
    }

    /** M5 的通知语义：传 noticeIds 就标记这几条，不传就全部已读 */
    public void markRead(Long userId, List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            noticeMapper.markAllRead(userId);
        } else {
            noticeMapper.markRead(userId, noticeIds);
        }
    }

    /** M11：落库 + 在线就用 NOTICE 帧推一下（不在线也没关系，M6 会读到） */
    public void create(MessageDtos.Notice req) {
        if (req.getUserId() == null || req.getTitle() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Notice n = new Notice();
        n.setUserId(req.getUserId());
        n.setType(req.getType() == null ? "SYSTEM" : req.getType());
        n.setTitle(req.getTitle());
        n.setContent(req.getContent() == null ? "" : req.getContent());
        n.setBizId(req.getBizId());
        n.setBizType(req.getBizType() == null ? "NONE" : req.getBizType());
        n.setReadFlag(0);
        n.setCreateTime(LocalDateTime.now());
        noticeMapper.insert(n);

        Map<String, Object> data = new HashMap<>();
        data.put("noticeId", String.valueOf(n.getId()));
        data.put("type", n.getType());
        data.put("title", n.getTitle());
        data.put("content", n.getContent());
        data.put("bizId", n.getBizId());
        data.put("bizType", n.getBizType());
        data.put("createTime", FMT.format(n.getCreateTime()));
        registry.send(n.getUserId(), Map.of("type", "NOTICE", "data", data));
    }
}
