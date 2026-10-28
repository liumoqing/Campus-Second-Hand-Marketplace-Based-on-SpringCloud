package com.campus.mall.product.controller;

import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.product.dto.CommentDTO;
import com.campus.mall.product.service.CommentService;
import com.campus.mall.product.vo.ProductVos.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商品评论区（P12~P14）。与订单评价（t_review）是两回事：这里不打分。
 */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** P12 评论列表：一级评论 + 每条附带的回复 */
    @GetMapping("/product/{id:\\d+}/comments")
    @RequireRole
    public R<PageResult<CommentVO>> list(@PathVariable("id") Long productId,
                                         @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                         @RequestParam(value = "sort", defaultValue = "new") String sort) {
        return R.ok(commentService.list(productId, pageNum, pageSize, sort));
    }

    /** P13 发表评论 / 回复评论 */
    @PostMapping("/product/{id:\\d+}/comments")
    @RequireRole
    public R<Map<String, Object>> create(@PathVariable("id") Long productId,
                                         @Valid @RequestBody CommentDTO dto) {
        return R.ok(commentService.create(UserContext.requireUserId(), productId, dto));
    }

    /** P14 删除自己的评论（ADMIN 可删任意） */
    @DeleteMapping("/product/comments/{id:\\d+}")
    @RequireRole
    public R<Void> delete(@PathVariable("id") Long commentId) {
        commentService.delete(UserContext.requireUserId(), commentId);
        return R.ok(null);
    }
}
