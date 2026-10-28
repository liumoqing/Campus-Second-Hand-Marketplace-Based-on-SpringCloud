package com.campus.mall.message.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** 取对方昵称头像、判断是否被封禁；取商品标题封面做卡片。 */
@FeignClient(name = "mall-user", path = "/inner/user")
public interface UserClient {

    @GetMapping("/{id}")
    R<Map<String, Object>> brief(@PathVariable("id") Long id);

    @GetMapping("/batch")
    R<List<Map<String, Object>>> batch(@RequestParam("ids") List<Long> ids);
}
