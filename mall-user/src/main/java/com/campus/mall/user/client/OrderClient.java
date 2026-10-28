package com.campus.mall.user.client;

import com.campus.mall.common.api.R;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 订单统计（03 §11 GET /inner/order/statistics/{userId}）。
 * mall-order 未启动/失败时降级为全 0，卖家卡片不允许因此 500（03 §3 U3 说明）。
 */
@FeignClient(name = "mall-order", path = "/inner/order")
public interface OrderClient {

    @GetMapping("/statistics/{userId}")
    R<OrderStatistics> statistics(@PathVariable("userId") Long userId);

    @Data
    class OrderStatistics {
        private Long soldCount = 0L;
        private Long boughtCount = 0L;
        private Double avgRating = 0D;
        private Long reviewCount = 0L;
        private Long pendingShipCount = 0L;
        private Long pendingReceiveCount = 0L;
    }
}
