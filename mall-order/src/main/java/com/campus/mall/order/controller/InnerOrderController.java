package com.campus.mall.order.controller;

import com.campus.mall.common.api.R;
import com.campus.mall.order.entity.Order;
import com.campus.mall.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单内部接口（O19~O22），只允许带 internal-token 的服务调用，网关不路由 /inner/**。
 */
@RestController
@RequestMapping("/inner/order")
@RequiredArgsConstructor
public class InnerOrderController {

    private final OrderService orderService;

    /** O19 供 payment 取订单金额/状态 */
    @GetMapping("/{orderNo}")
    public R<Map<String, Object>> get(@PathVariable("orderNo") String orderNo) {
        Order o = orderService.requireOrder(orderNo);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", o.getOrderNo());
        data.put("buyerId", o.getBuyerId());
        data.put("sellerId", o.getSellerId());
        data.put("productId", o.getProductId());
        data.put("totalAmount", o.getTotalAmount());
        data.put("freight", o.getFreight());
        data.put("payAmount", o.getPayAmount());
        data.put("status", o.getStatus());
        return R.ok(data);
    }

    /** O20 支付成功通知（payment 同步调用；MQ 兜底也走这里） */
    @PostMapping("/{orderNo}/notify-paid")
    public R<Boolean> notifyPaid(@PathVariable("orderNo") String orderNo) {
        return R.ok(orderService.notifyPaid(orderNo));
    }

    /** O21 退款完成通知（payment 调用） */
    @PostMapping("/{orderNo}/notify-refunded")
    public R<Boolean> notifyRefunded(@PathVariable("orderNo") String orderNo) {
        return R.ok(orderService.notifyRefunded(orderNo));
    }

    /** O22 用户维度统计（mall-user 的 U3/U4 用） */
    @GetMapping("/statistics/{userId:\\d+}")
    public R<Map<String, Object>> statistics(@PathVariable("userId") Long userId) {
        return R.ok(orderService.statistics(userId));
    }
}
