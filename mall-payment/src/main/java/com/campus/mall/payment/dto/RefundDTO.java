package com.campus.mall.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Y4 退款（由 mall-order 在退款/售后被同意时调用）。 */
@Data
public class RefundDTO {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "退款金额不能为空")
    @DecimalMin(value = "0.01", message = "退款金额必须大于 0")
    private BigDecimal amount;

    private String reason;
}
