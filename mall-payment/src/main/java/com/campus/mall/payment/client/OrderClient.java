package com.campus.mall.payment.client;

import com.campus.mall.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 调 mall-order 的内部接口（03 §10 O19/O20）。
 * 没有 circuitbreaker 依赖，所以不写 fallbackFactory，降级由 PaymentService try/catch 处理。
 */
@FeignClient(name = "mall-order", path = "/inner/order")
public interface OrderClient {

    @GetMapping("/{orderNo}")
    R<Map<String, Object>> getByOrderNo(@PathVariable("orderNo") String orderNo);

    @PostMapping("/{orderNo}/notify-paid")
    R<Boolean> notifyPaid(@PathVariable("orderNo") String orderNo, Map<String, Object> body);

    @PostMapping("/{orderNo}/notify-refunded")
    R<Boolean> notifyRefunded(@PathVariable("orderNo") String orderNo, Map<String, Object> body);

    /** 仅内部类型用：把 Map 里的金额转成 BigDecimal */
    static BigDecimal amountOf(Map<String, Object> order) {
        Object v = order == null ? null : order.get("payAmount");
        return v == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
    }
}
