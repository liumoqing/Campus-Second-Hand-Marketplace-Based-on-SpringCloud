package com.campus.mall.order.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.R;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.order.client.PaymentClient;
import com.campus.mall.order.client.ProductClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/** 订单域关键外部调用：由 Sentinel 负责熔断，并在网络异常时返回统一降级结果。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExternalService {

    private final ProductClient productClient;
    private final PaymentClient paymentClient;

    @SentinelResource(value = "orderProductView", fallback = "productViewFallback",
            exceptionsToIgnore = BizException.class)
    public Map<String, Object> viewProduct(Long productId) {
        R<Map<String, Object>> response = productClient.view(productId);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return response.getData();
    }

    public Map<String, Object> productViewFallback(Long productId, Throwable error) {
        log.warn("商品服务调用已降级：productId={}, err={}", productId, error.getMessage());
        throw new BizException(ErrorCode.SERVICE_DEGRADED, "商品服务暂时不可用，请稍后再试");
    }

    @SentinelResource(value = "orderPaymentRefund", fallback = "paymentRefundFallback",
            exceptionsToIgnore = BizException.class)
    public void refund(String orderNo, BigDecimal amount, String reason) {
        R<Map<String, Object>> response = paymentClient.refund(new PaymentClient.RefundBody(orderNo, amount, reason));
        if (response == null || response.getCode() != 0) {
            String message = response == null ? "支付服务无响应" : response.getMessage();
            throw new BizException(ErrorCode.SERVICE_DEGRADED, message);
        }
    }

    public void paymentRefundFallback(String orderNo, BigDecimal amount, String reason, Throwable error) {
        log.warn("支付退款调用已降级：orderNo={}, amount={}, err={}", orderNo, amount, error.getMessage());
        throw new BizException(ErrorCode.SERVICE_DEGRADED, "支付服务暂时不可用，请稍后再试");
    }
}
