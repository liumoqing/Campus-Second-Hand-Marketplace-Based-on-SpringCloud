package com.campus.mall.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** O12 退款 / 售后申请 + O13 卖家处理。 */
public class RefundDtos {

    /** 申请：orderNo 由路径传入（O12 = POST /order/{orderNo}/refund），金额不传=全额（pay_amount） */
    @Data
    public static class Apply {
        private String orderNo;
        private BigDecimal amount;
        @NotBlank(message = "请填写退款原因")
        @Size(max = 255, message = "原因最多 255 字")
        private String reason;
    }

    /** 处理：agree=true 同意（走支付服务退款），false 拒绝（订单按 beforeStatus 回退） */
    @Data
    public static class Handle {
        @NotNull(message = "请选择同意或拒绝")
        private Boolean agree;
        @Size(max = 255, message = "备注最多 255 字")
        private String remark;
    }
}
