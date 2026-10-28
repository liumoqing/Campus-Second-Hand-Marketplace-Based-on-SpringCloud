package com.campus.mall.order.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/** 调 mall-user 的内部接口：地址快照、评价人昵称头像、信用分扣减。 */
@FeignClient(name = "mall-user", path = "/inner/user")
public interface UserClient {

    @GetMapping("/{id}")
    R<Map<String, Object>> brief(@PathVariable("id") Long id);

    @GetMapping("/address/{addressId}")
    R<Map<String, Object>> address(@PathVariable("addressId") Long addressId, @RequestParam("userId") Long userId);

    /** 评价低于 2 星时扣信用分（01 §8.7） */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/credit")
    R<Void> deductCredit(@PathVariable("id") Long id, @RequestParam("score") int score);
}
