package com.campus.mall.product.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 调 mall-message 的内部通知接口（M11）。 */
@FeignClient(name = "mall-message", path = "/inner/message")
public interface MessageClient {

    @PostMapping("/notice")
    R<Void> notice(@RequestBody NoticeBody body);

    record NoticeBody(Long userId, String type, String title, String content, String bizId, String bizType) {
    }
}
