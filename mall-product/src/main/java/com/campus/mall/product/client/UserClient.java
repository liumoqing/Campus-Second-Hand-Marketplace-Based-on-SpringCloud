package com.campus.mall.product.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** 调 mall-user 的内部接口（商品详情页的卖家卡片、下架通知的接收人信息）。 */
@FeignClient(name = "mall-user", path = "/inner/user")
public interface UserClient {

    @GetMapping("/{id}")
    R<Map<String, Object>> brief(@PathVariable("id") Long id);

    @GetMapping("/batch")
    R<List<Map<String, Object>>> batch(@RequestParam("ids") List<Long> ids);
}
