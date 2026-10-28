package com.campus.mall.product.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/** 调 mall-order 的内部统计接口（商品详情页卖家卡片的"卖出件数/评分"）。 */
@FeignClient(name = "mall-order", path = "/inner/order")
public interface OrderClient {

    @GetMapping("/statistics/{userId}")
    R<Map<String, Object>> statistics(@PathVariable("userId") Long userId);
}
