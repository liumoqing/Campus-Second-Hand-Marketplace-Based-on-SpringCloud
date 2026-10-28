package com.campus.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.order.client.UserClient;
import com.campus.mall.order.dto.ReviewDTO;
import com.campus.mall.order.entity.Order;
import com.campus.mall.order.entity.Review;
import com.campus.mall.order.mapper.OrderMapper;
import com.campus.mall.order.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * O7~O11 双向评价（01 §8.7）。
 *
 * <p>方向由调用者在本单里的角色自动判定，客户端传不了：买家 → BUYER_TO_SELLER，卖家 → SELLER_TO_BUYER。
 * 一个订单每个方向只有一条（02 的 {@code uk_order_direction}），重复提交由唯一键 + 预检查一起拦。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final String BUYER_TO_SELLER = "BUYER_TO_SELLER";
    private static final String SELLER_TO_BUYER = "SELLER_TO_BUYER";

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final UserClient userClient;

    // ---------------- O7 提交评价 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, String orderNo, ReviewDTO dto) {
        Order order = orderService.requireOrder(orderNo);
        boolean isBuyer = Objects.equals(order.getBuyerId(), userId);
        boolean isSeller = Objects.equals(order.getSellerId(), userId);
        if (!isBuyer && !isSeller) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_NOT_ALLOWED, "只有已完成的订单可以评价");
        }
        String direction = isBuyer ? BUYER_TO_SELLER : SELLER_TO_BUYER;
        Long revieweeId = isBuyer ? order.getSellerId() : order.getBuyerId();
        if (reviewMapper.existsByDirection(order.getId(), direction) > 0) {
            throw new BizException(ErrorCode.REVIEW_DUPLICATED);
        }

        Review review = new Review();
        review.setOrderId(order.getId());
        review.setProductId(order.getProductId());
        review.setDirection(direction);
        review.setReviewerId(userId);
        review.setRevieweeId(revieweeId);
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setImages(dto.getImages() == null || dto.getImages().isEmpty()
                ? null
                : cn.hutool.json.JSONUtil.toJsonStr(dto.getImages()));
        review.setAnonymous(Boolean.TRUE.equals(dto.getAnonymous()) ? 1 : 0);
        review.setCreateTime(LocalDateTime.now());
        reviewMapper.insert(review);

        LocalDateTime now = LocalDateTime.now();
        if (isBuyer) {
            orderMapper.markBuyerReviewed(order.getId(), now);
        } else {
            orderMapper.markSellerReviewed(order.getId(), now);
        }
        // BR-23：≤2 星扣被评人信用分 2 分（失败不影响评价）
        if (dto.getRating() != null && dto.getRating() <= 2) {
            try {
                userClient.deductCredit(revieweeId, 2);
            } catch (Exception e) {
                log.warn("扣信用分失败：userId={}, err={}", revieweeId, e.getMessage());
            }
        }
        return Map.of("reviewId", review.getId(), "direction", direction);
    }

    // ---------------- O8 回复评价 ----------------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reply(Long userId, Long reviewId, String content) {
        if (content == null || content.isBlank() || content.length() > 200) {
            throw new BizException(ErrorCode.MESSAGE_CONTENT_INVALID, "回复内容 1~200 字");
        }
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "评价不存在");
        }
        if (!Objects.equals(review.getRevieweeId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有被评价人可以回复");
        }
        review.setReply(content);
        review.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(review);
        return Map.of("reviewId", reviewId);
    }

    // ---------------- O9 我写的评价 ----------------

    public PageResult<Map<String, Object>> mine(Long userId, int pageNum, int pageSize) {
        return pageBy(new LambdaQueryWrapper<Review>()
                .eq(Review::getReviewerId, userId).orderByDesc(Review::getCreateTime), pageNum, pageSize, true);
    }

    // ---------------- O10 我收到的评价 ----------------

    public PageResult<Map<String, Object>> received(Long userId, int pageNum, int pageSize) {
        return pageBy(new LambdaQueryWrapper<Review>()
                .eq(Review::getRevieweeId, userId).orderByDesc(Review::getCreateTime), pageNum, pageSize, true);
    }

    // ---------------- O11 某用户主页的评价（公开，只取卖家被评方向） ----------------

    public PageResult<Map<String, Object>> ofUser(Long userId, int pageNum, int pageSize) {
        return pageBy(new LambdaQueryWrapper<Review>()
                .eq(Review::getRevieweeId, userId)
                .eq(Review::getDirection, BUYER_TO_SELLER)
                .orderByDesc(Review::getCreateTime), pageNum, pageSize, false);
    }

    // ---------------- 后台（O18 的一部分） ----------------

    public PageResult<Map<String, Object>> adminPage(int pageNum, int pageSize, Long productId) {
        return pageBy(new LambdaQueryWrapper<Review>()
                .eq(productId != null, Review::getProductId, productId)
                .orderByDesc(Review::getCreateTime), pageNum, pageSize, true);
    }

    /** 卖家星级汇总：{avgRating, reviewCount} */
    public Map<String, Object> ratingOf(Long userId) {
        Map<String, Object> r = reviewMapper.ratingOf(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        Object avg = r == null ? null : r.get("avgRating");
        data.put("avgRating", avg == null ? 5.00 : avg);
        data.put("reviewCount", r == null || r.get("reviewCount") == null ? 0 : r.get("reviewCount"));
        return data;
    }

    private PageResult<Map<String, Object>> pageBy(LambdaQueryWrapper<Review> w, int pageNum, int pageSize, boolean hideAnonymousName) {
        Page<Review> page = reviewMapper.selectPage(new Page<>(Math.max(1, pageNum), Math.min(50, Math.max(1, pageSize))), w);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Review r : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reviewId", r.getId());
            row.put("orderId", r.getOrderId());
            row.put("productId", r.getProductId());
            row.put("direction", r.getDirection());
            row.put("rating", r.getRating());
            row.put("content", r.getContent());
            row.put("images", r.getImages() == null ? List.of() : cn.hutool.json.JSONUtil.toList(r.getImages(), String.class));
            row.put("anonymous", r.getAnonymous() != null && r.getAnonymous() == 1);
            row.put("reply", r.getReply());
            row.put("replyTime", r.getReplyTime());
            row.put("createTime", r.getCreateTime());
            boolean anon = r.getAnonymous() != null && r.getAnonymous() == 1;
            if (anon && hideAnonymousName) {
                row.put("reviewerNickname", "匿名用户");
                row.put("reviewerAvatar", null);
            } else {
                Map<String, Object> u = safeBrief(r.getReviewerId());
                row.put("reviewerNickname", u.get("nickname"));
                row.put("reviewerAvatar", u.get("avatar"));
            }
            rows.add(row);
        }
        return PageResult.from(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private Map<String, Object> safeBrief(Long userId) {
        try {
            Map<String, Object> data = userClient.brief(userId).getData();
            return data == null ? Map.of() : data;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
