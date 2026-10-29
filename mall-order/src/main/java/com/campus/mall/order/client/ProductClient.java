package com.campus.mall.order.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 调 mall-product 的内部接口（P27~P31）。 */
@FeignClient(name = "mall-product", path = "/inner/product")
public interface ProductClient {

    @GetMapping("/{id}")
    R<Map<String, Object>> view(@PathVariable("id") Long id);

    @PostMapping("/batch")
    R<List<Map<String, Object>>> batch(@RequestBody Map<String, List<Long>> body);

    @PostMapping("/stock/deduct")
    R<Void> deduct(@RequestBody StockBody body);

    @PostMapping("/stock/restore")
    R<Void> restore(@RequestBody StockBody body);

    @PostMapping("/sold")
    R<Void> sold(@RequestBody StockBody body);

    /** 商品价格取自商品服务，避免前端传价 */
    static BigDecimal priceOf(Map<String, Object> view) {
        Object p = view == null ? null : view.get("price");
        return p == null ? null : new BigDecimal(String.valueOf(p));
    }

    static String textOf(Map<String, Object> view, String key) {
        Object value = view == null ? null : view.get(key);
        return value == null ? null : String.valueOf(value);
    }

    static String titleOf(Map<String, Object> view) {
        return textOf(view, "title");
    }

    static String coverOf(Map<String, Object> view) {
        return textOf(view, "coverUrl");
    }

    record StockBody(Long productId, String orderNo) {
    }
}
