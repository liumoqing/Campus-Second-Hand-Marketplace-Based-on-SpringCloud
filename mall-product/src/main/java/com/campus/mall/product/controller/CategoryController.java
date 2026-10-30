package com.campus.mall.product.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import com.campus.mall.common.api.R;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.product.entity.Category;
import com.campus.mall.product.service.CategoryService;
import com.campus.mall.product.vo.ProductVos.CategoryNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 分类（P19~P23）。首页左侧分类栏走公开接口；增删改只给 ADMIN。
 */
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** P19 分类树（公开，只含启用项，两级） */
    @GetMapping("/tree")
    public R<List<CategoryNode>> tree() {
        return R.ok(categoryService.tree(true));
    }

    /** P20 后台分类树（含停用项与 status） */
    @GetMapping("/admin/tree")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<List<CategoryNode>> adminTree() {
        return R.ok(categoryService.tree(false));
    }

    /** P21 新建分类 */
    @PostMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<Map<String, Object>> create(@RequestBody Category body) {
        return R.ok(Map.of("categoryId", categoryService.create(body)));
    }

    /** P22 改分类（名称/图标/排序/启停） */
    @PutMapping("/admin/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<Boolean> update(@PathVariable("id") Long id, @RequestBody Category body) {
        return R.ok(categoryService.update(id, body));
    }

    /** P23 删分类（有商品或子分类则拒绝） */
    @DeleteMapping("/admin/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<Boolean> delete(@PathVariable("id") Long id) {
        return R.ok(categoryService.delete(id));
    }
}
