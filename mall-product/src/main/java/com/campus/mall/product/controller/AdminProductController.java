package com.campus.mall.product.controller;

import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.product.dto.ProductQuery;
import com.campus.mall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台商品管理（P24~P26）。无审核流程，管理员的治理手段只有"强制下架"。
 */
@RestController
@RequestMapping("/product/admin")
@RequiredArgsConstructor
@RequireRole(MallConstants.ROLE_ADMIN)
public class AdminProductController {

    private final ProductService productService;

    /** P24 商品管理列表（含已下架/草稿） */
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(ProductQuery query) {
        return R.ok(productService.adminPage(query));
    }

    /** P25 强制下架违规商品 */
    @PutMapping("/{id:\\d+}/off-shelf")
    public R<Boolean> offShelf(@PathVariable("id") Long id,
                               @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return R.ok(productService.adminOffShelf(id, reason));
    }

    /** P26 商品概览 */
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(productService.adminStatistics());
    }
}
