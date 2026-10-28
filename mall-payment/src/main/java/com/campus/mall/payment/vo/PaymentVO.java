package com.campus.mall.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Y2 支付单详情 / Y1 响应共用结构。 */
@Data
public class PaymentVO {

    private String payNo;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private BigDecimal payAmount;
    private String channel;
    private String status;
    private String tradeNo;
    private LocalDateTime payTime;
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
}
