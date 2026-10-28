package com.campus.mall.product.controller;

import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.product.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 收藏（P15~P17）。收藏/取消都幂等，重复操作不报错；favorite_count 同事务增减。
 */
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** P15 收藏 */
    @PostMapping("/{productId:\\d+}")
    @RequireRole
    public R<Map<String, Object>> add(@PathVariable("productId") Long productId) {
        return R.ok(favoriteService.add(UserContext.requireUserId(), productId));
    }

    /** P16 取消收藏 */
    @DeleteMapping("/{productId:\\d+}")
    @RequireRole
    public R<Map<String, Object>> remove(@PathVariable("productId") Long productId) {
        return R.ok(favoriteService.remove(UserContext.requireUserId(), productId));
    }

    /** P17 我的收藏 */
    @GetMapping("/page")
    @RequireRole
    public R<PageResult<Map<String, Object>>> page(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "12") int pageSize) {
        return R.ok(favoriteService.page(UserContext.requireUserId(), pageNum, pageSize));
    }
}
