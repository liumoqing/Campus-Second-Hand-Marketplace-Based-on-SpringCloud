package com.campus.mall.order.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.Map;

/** 调 mall-payment 的内部接口（Y7/Y8）。退款是"payment 本地改单 → MQ → order 消费"，这里是同步兜底入口。 */
@FeignClient(name = "mall-payment", path = "/inner/payment")
public interface PaymentClient {

    @GetMapping("/order/{orderNo}")
    R<Map<String, Object>> byOrderNo(@PathVariable("orderNo") String orderNo);

    @PostMapping("/refund")
    R<Map<String, Object>> refund(@RequestBody RefundBody body);

    record RefundBody(String orderNo, BigDecimal amount, String reason) {
    }
}
