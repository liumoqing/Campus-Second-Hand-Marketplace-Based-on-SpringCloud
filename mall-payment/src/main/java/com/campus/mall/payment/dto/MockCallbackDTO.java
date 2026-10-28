package com.campus.mall.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Y3 模拟支付回调（留给支付宝沙箱；v1 前端不调）。 */
@Data
public class MockCallbackDTO {

    @NotBlank(message = "支付单号不能为空")
    private String payNo;

    private String tradeNo;

    @NotBlank(message = "状态不能为空")
    private String status;

    /** md5(payNo + tradeNo + status + secret)，secret 取 mall.payment.mock.secret */
    @NotBlank(message = "签名不能为空")
    private String sign;
}
