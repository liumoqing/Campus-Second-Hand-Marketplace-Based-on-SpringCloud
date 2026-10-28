package com.campus.mall.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Y1 支付请求：只需要订单号，金额以订单为准（不接受前端传金额，防止改价）。 */
@Data
public class PayDTO {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
