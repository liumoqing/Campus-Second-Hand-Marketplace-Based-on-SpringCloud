package com.campus.mall.product.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.product.client.UserClient;
import com.campus.mall.product.dto.CommentDTO;
import com.campus.mall.product.entity.ProductComment;
import com.campus.mall.product.mapper.ProductCommentMapper;
import com.campus.mall.product.mapper.ProductMapper;
import com.campus.mall.product.vo.ProductVos.CommentUserVO;
import com.campus.mall.product.vo.ProductVos.CommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品评论区（P12~P14）。商品评论不打分、不影响卖家评分（评分只来自订单双向互评）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final String RATE_KEY = "mall:product:comment-rate:";
    private static final int RATE_LIMIT = 3;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final ProductCommentMapper commentMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final UserClient userClient;
    private final StringRedisTemplate redis;

    public PageResult<CommentVO> list(Long productId, int pageNum, int pageSize, String sort) {
        productService.requireProduct(productId);
        LambdaQueryWrapper<ProductComment> w = new LambdaQueryWrapper<ProductComment>()
                .eq(ProductComment::getProductId, productId)
                .eq(ProductComment::getParentId, 0L)
                .eq(ProductComment::getStatus, 1)
                .orderByDesc(ProductComment::getCreateTime);
        Page<ProductComment> page = commentMapper.selectPage(new Page<>(Math.max(1, pageNum), Math.min(50, Math.max(1, pageSize))), w);
        List<ProductComment> tops = page.getRecords();

        List<Long> parentIds = tops.stream().map(ProductComment::getId).toList();
        List<ProductComment> replies = parentIds.isEmpty() ? Collections.emptyList()
                : commentMapper.selectList(new LambdaQueryWrapper<ProductComment>()
                .in(ProductComment::getParentId, parentIds)
                .eq(ProductComment::getStatus, 1)
                .orderByAsc(ProductComment::getCreateTime));
        Map<Long, List<ProductComment>> replyMap = replies.stream()
                .collect(Collectors.groupingBy(ProductComment::getParentId, LinkedHashMap::new, Collectors.toList()));

        Set<Long> userIds = new java.util.HashSet<>();
        tops.forEach(c -> userIds.add(c.getUserId()));
        replies.forEach(c -> userIds.add(c.getUserId()));
        Map<Long, CommentUserVO> users = users(userIds);

        List<CommentVO> records = new ArrayList<>();
        for (ProductComment c : tops) {
            CommentVO vo = toVO(c, users);
            List<ProductComment> rs = replyMap.getOrDefault(c.getId(), List.of());
            vo.setReplyCount((long) rs.size());
            vo.setReplies(rs.stream().map(r -> toVO(r, users)).toList());
            records.add(vo);
        }
        if ("hot".equals(sort)) {
            records.sort((a, b) -> Long.compare(b.getReplyCount(), a.getReplyCount()));
        }
        return PageResult.from(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Long productId, CommentDTO dto) {
        productService.requireProduct(productId);
        checkRate(userId);
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评论内容不能为空");
        }
        if (dto.getContent().length() > 500) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评论最多 500 字");
        }
        if (dto.getImages() != null && dto.getImages().size() > 3) {
            throw new BizException(ErrorCode.PARAM_INVALID, "最多 3 张图");
        }
        long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId != 0L) {
            ProductComment parent = commentMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getProductId(), productId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "被回复的评论不存在");
            }
            if (parent.getParentId() != null && parent.getParentId() != 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "只能回复一级评论");
            }
        }
        ProductComment c = new ProductComment();
        c.setProductId(productId);
        c.setUserId(userId);
        c.setParentId(parentId);
        c.setContent(dto.getContent());
        c.setImages(dto.getImages() == null || dto.getImages().isEmpty() ? null : JSONUtil.toJsonStr(dto.getImages()));
        c.setStatus(1);
        commentMapper.insert(c);
        try {
            productMapper.incrComment(productId, 1);
        } catch (Exception e) {
            log.warn("评论数自增失败：productId={}", productId);
        }
        return Map.of("commentId", c.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long commentId) {
        ProductComment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评论不存在");
        }
        if (!Objects.equals(c.getUserId(), userId) && !UserContext.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }
        int removed = 1;
        if (c.getParentId() != null && c.getParentId() == 0L) {
            List<ProductComment> children = commentMapper.selectList(new LambdaQueryWrapper<ProductComment>()
                    .eq(ProductComment::getParentId, commentId));
            for (ProductComment child : children) {
                commentMapper.deleteById(child.getId());
                removed++;
            }
        }
        commentMapper.deleteById(commentId);
        try {
            productMapper.incrComment(c.getProductId(), -removed);
        } catch (Exception e) {
            log.warn("评论数扣减失败：productId={}", c.getProductId());
        }
    }

    private void checkRate(Long userId) {
        String key = RATE_KEY + userId;
        try {
            // 用 SET NX EX 建窗口，避免 RedisTemplate.expire(...) —— Spring Data Redis 4.x 的
            // DefaultedRedisConnection#pExpire 会自递归导致 StackOverflowError
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", RATE_WINDOW);
            Long n = Boolean.TRUE.equals(first) ? 1L : redis.opsForValue().increment(key);
            if (n != null && n > RATE_LIMIT) {
                throw new BizException(ErrorCode.PARAM_INVALID, "评论太快了，1 分钟最多 3 条");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("评论限流检查失败，放行：{}", e.getMessage());
        }
    }

    private Map<Long, CommentUserVO> users(Set<Long> ids) {
        Map<Long, CommentUserVO> map = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        try {
            R<List<Map<String, Object>>> r = userClient.batch(new ArrayList<>(ids));
            if (r != null && r.getData() != null) {
                for (Map<String, Object> u : r.getData()) {
                    CommentUserVO vo = new CommentUserVO();
                    vo.setId(ProductService.longOf(u.get("id")));
                    vo.setNickname(ProductService.str(u.get("nickname")));
                    vo.setAvatar(ProductService.str(u.get("avatar")));
                    map.put(vo.getId(), vo);
                }
            }
        } catch (Exception e) {
            log.warn("评论用户信息批量查询失败：{}", e.getMessage());
        }
        return map;
    }

    private CommentVO toVO(ProductComment c, Map<Long, CommentUserVO> users) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setParentId(c.getParentId());
        vo.setContent(c.getContent());
        vo.setImages(parseImages(c.getImages()));
        vo.setCreateTime(c.getCreateTime());
        CommentUserVO u = users.get(c.getUserId());
        if (u == null) {
            u = new CommentUserVO();
            u.setId(c.getUserId());
            u.setNickname("用户" + c.getUserId());
        }
        vo.setUser(u);
        return vo;
    }

    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
