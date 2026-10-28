package com.campus.mall.payment.controller;

import com.campus.mall.common.api.R;
import com.campus.mall.payment.dto.RefundDTO;
import com.campus.mall.payment.service.PaymentService;
import com.campus.mall.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付服务的内部接口（03 §10 Y7~Y9，网关不路由 /inner/**，统一校验 internal-token）。
 */
@RestController
@RequestMapping("/inner/payment")
@RequiredArgsConstructor
public class InnerPaymentController {

    private final PaymentService paymentService;

    /** Y7 按订单号取支付单（mall-order 用） */
    @GetMapping("/order/{orderNo}")
    public R<PaymentVO> byOrderNo(@PathVariable String orderNo) {
        return R.ok(paymentService.detailByOrderNo(orderNo));
    }

    /** Y8 退款（mall-order 在退款/售后被同意时调用） */
    @PostMapping("/refund")
    public R<Map<String, Object>> refund(@RequestBody @Valid RefundDTO dto) {
        boolean ok = paymentService.refund(dto);
        return R.ok(Map.of("refunded", ok, "orderNo", dto.getOrderNo()));
    }
}
