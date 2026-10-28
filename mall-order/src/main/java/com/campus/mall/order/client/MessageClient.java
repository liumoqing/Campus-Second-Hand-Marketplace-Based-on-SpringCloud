package com.campus.mall.order.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 调 mall-message 的内部接口（M13 订单卡片）。
 * 调用失败只记日志，绝不阻塞发货/收货主流程（01 §8.11）。
 */
@FeignClient(name = "mall-message", path = "/inner/message")
public interface MessageClient {

    @PostMapping("/card")
    R<Map<String, Object>> card(@RequestBody CardBody body);

    record CardBody(String orderNo, String cardType, Long buyerId, Long sellerId,
                    Long productId, String productTitle, String statusText, String actionText) {
    }
}
