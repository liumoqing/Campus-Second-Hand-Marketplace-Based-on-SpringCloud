package com.campus.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.product.entity.Category;
import com.campus.mall.product.entity.Product;
import com.campus.mall.product.mapper.CategoryMapper;
import com.campus.mall.product.mapper.ProductMapper;
import com.campus.mall.product.vo.ProductVos.CategoryNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分类（P19~P23）：两级；启用态做进程内缓存 + 管理员改动后清空（本机没装 Redis 客户端依赖的
 * 序列化器，用 ConcurrentHashMap 缓存即可，语义与 01 §7.9 的 mall:category:tree 一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    /** 简易缓存：key=onlyEnabled */
    private final Map<String, List<CategoryNode>> cache = new ConcurrentHashMap<>();

    public List<CategoryNode> tree(boolean onlyEnabled) {
        String key = onlyEnabled ? "enabled" : "all";
        return cache.computeIfAbsent(key, k -> build(onlyEnabled));
    }

    private List<CategoryNode> build(boolean onlyEnabled) {
        List<Category> all = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(onlyEnabled, Category::getStatus, 1)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        Map<Long, CategoryNode> idMap = new LinkedHashMap<>();
        List<CategoryNode> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryNode n = toNode(c);
            idMap.put(c.getId(), n);
            if (c.getParentId() == null || c.getParentId() == 0) {
                roots.add(n);
            }
        }
        for (Category c : all) {
            if (c.getParentId() != null && c.getParentId() != 0) {
                CategoryNode parent = idMap.get(c.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(idMap.get(c.getId()));
                }
            }
        }
        return roots;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Category body) {
        validateName(body);
        Long parentId = body.getParentId() == null ? 0L : body.getParentId();
        if (parentId != 0) {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
            }
            if (parent.getParentId() != null && parent.getParentId() != 0) {
                // 只允许两级：不能挂在一级分类的孩子下面
                throw new BizException(ErrorCode.CATEGORY_LEVEL_EXCEEDED);
            }
        }
        Long dup = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, parentId)
                .eq(Category::getName, body.getName()));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同级已有同名分类");
        }
        Category c = new Category();
        c.setParentId(parentId);
        c.setName(body.getName());
        c.setIcon(body.getIcon());
        c.setSort(body.getSort() == null ? 0 : body.getSort());
        c.setStatus(body.getStatus() == null ? 1 : body.getStatus());
        categoryMapper.purgeSoftDeleted(parentId, c.getName());
        categoryMapper.insert(c);
        cache.clear();
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean update(Long id, Category body) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (body.getName() != null && !body.getName().isBlank()) {
            c.setName(body.getName());
        }
        if (body.getIcon() != null) {
            c.setIcon(body.getIcon());
        }
        if (body.getSort() != null) {
            c.setSort(body.getSort());
        }
        if (body.getStatus() != null) {
            c.setStatus(body.getStatus());
        }
        // 改名/改父级前先清掉同名软删残留，避免撞 uk_parent_name
        categoryMapper.purgeSoftDeleted(c.getParentId() == null ? 0L : c.getParentId(), c.getName());
        categoryMapper.updateById(c);
        cache.clear();
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        Long used = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, id));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_PRODUCT);
        }
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_PRODUCT, "该分类下还有子分类，请先删除子分类");
        }
        // 物理删：唯一键 uk_parent_name(parent_id,name,deleted) 含 deleted，软删同名会撞键
        categoryMapper.hardDelete(id);
        cache.clear();
        return true;
    }

    /** 分类是否存在且启用（发布时校验） */
    public Category requireEnabled(Long id) {
        Category c = id == null ? null : categoryMapper.selectById(id);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return c;
    }

    public String nameOf(Long id) {
        Category c = id == null ? null : categoryMapper.selectById(id);
        return c == null ? null : c.getName();
    }

    public void clearCache() {
        cache.clear();
        log.debug("分类缓存已清空（key 前缀 {}）", MallConstants.CACHE_CATEGORY_TREE);
    }

    private static CategoryNode toNode(Category c) {
        CategoryNode n = new CategoryNode();
        n.setId(c.getId());
        n.setParentId(c.getParentId());
        n.setName(c.getName());
        n.setIcon(c.getIcon());
        n.setSort(c.getSort());
        n.setStatus(c.getStatus());
        return n;
    }

    private static void validateName(Category body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类名不能为空");
        }
        if (body.getName().length() > 32) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类名最多 32 字");
        }
    }
}
