package com.campus.mall.message.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/** 取商品信息做 PRODUCT 卡片（走 mall-product 的 /inner/product/{id} 与 /batch）。 */
@FeignClient(name = "mall-product", path = "/inner/product")
public interface ProductClient {

    @GetMapping("/{id}")
    R<Map<String, Object>> view(@PathVariable("id") Long id);

    @PostMapping("/batch")
    R<List<Map<String, Object>>> batch(@RequestBody List<Long> ids);
}
