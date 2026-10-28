package com.campus.mall.user.client;

import com.campus.mall.common.api.R;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品统计（03 §9 GET /inner/product/statistics/{sellerId} → {onSaleCount,draftCount,soldCount}）。
 */
@FeignClient(name = "mall-product", path = "/inner/product")
public interface ProductClient {

    @GetMapping("/statistics/{sellerId}")
    R<ProductStatistics> statistics(@PathVariable("sellerId") Long sellerId);

    @Data
    class ProductStatistics {
        private Long onSaleCount = 0L;
        private Long draftCount = 0L;
        private Long soldCount = 0L;
        /** t_favorite 在商品库，收藏数一起由 mall-product 给 */
        private Long favoriteCount = 0L;
    }
}
