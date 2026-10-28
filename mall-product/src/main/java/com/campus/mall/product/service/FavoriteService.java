package com.campus.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.product.entity.Favorite;
import com.campus.mall.product.entity.Product;
import com.campus.mall.product.mapper.FavoriteMapper;
import com.campus.mall.product.mapper.ProductMapper;
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
 * 收藏（P15~P17）。插入/删除与 t_product.favorite_count 增减在同一事务里，重复操作幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> add(Long userId, Long productId) {
        Product p = productService.requireProduct(productId);
        Long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getProductId, productId));
        if (exists == null || exists == 0) {
            Favorite f = new Favorite();
            f.setUserId(userId);
            f.setProductId(productId);
            f.setCreateTime(LocalDateTime.now());
            favoriteMapper.insert(f);
            try {
                productMapper.incrFavorite(productId, 1);
            } catch (Exception e) {
                log.warn("收藏数自增失败：productId={}", productId);
            }
        }
        return Map.of("favorited", true, "favoriteCount", countOf(productId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> remove(Long userId, Long productId) {
        int removed = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getProductId, productId));
        if (removed > 0) {
            try {
                productMapper.incrFavorite(productId, -1);
            } catch (Exception e) {
                log.warn("收藏数扣减失败：productId={}", productId);
            }
        }
        return Map.of("favorited", false, "favoriteCount", countOf(productId));
    }

    public PageResult<Map<String, Object>> page(Long userId, int pageNum, int pageSize) {
        Page<Favorite> page = favoriteMapper.selectPage(new Page<>(Math.max(1, pageNum), Math.min(50, Math.max(1, pageSize))),
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId).orderByDesc(Favorite::getCreateTime));
        List<Map<String, Object>> records = new ArrayList<>();
        for (Favorite f : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("favoriteId", f.getId());
            row.put("productId", f.getProductId());
            row.put("createTime", f.getCreateTime());
            Product p = productMapper.selectById(f.getProductId());
            if (p != null) {
                row.put("title", p.getTitle());
                row.put("price", p.getPrice());
                row.put("coverUrl", p.getCoverUrl());
                row.put("status", p.getStatus());
                Map<String, Object> seller = null;
                try {
                    seller = productService.briefOf(p.getSellerId());
                } catch (Exception e) {
                    log.debug("收藏列表取卖家信息失败：{}", e.getMessage());
                }
                row.put("sellerNickname", seller == null ? null : seller.get("nickname"));
            } else {
                row.put("status", "DELETED");
            }
            records.add(row);
        }
        return PageResult.from(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public boolean favorited(Long userId, Long productId) {
        Long n = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId).eq(Favorite::getProductId, productId));
        return n != null && n > 0;
    }

    private int countOf(Long productId) {
        Product p = productMapper.selectById(productId);
        return p == null || p.getFavoriteCount() == null ? 0 : p.getFavoriteCount();
    }

    /** 收藏者列表（商品下架时发通知用，v1 暂不暴露接口） */
    public List<Long> userIdsOf(Long productId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>().eq(Favorite::getProductId, productId))
                .stream().map(Favorite::getUserId).filter(Objects::nonNull).distinct().toList();
    }

    public void requireExists(Long userId, Long productId) {
        if (!favorited(userId, productId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "还没有收藏该商品");
        }
    }
}
