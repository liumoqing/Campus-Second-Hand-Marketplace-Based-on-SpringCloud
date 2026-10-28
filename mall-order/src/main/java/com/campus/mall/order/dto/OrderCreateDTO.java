package com.campus.mall.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** O1 下单：只传商品与地址，金额一律由服务端算（防前端改价）。 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "商品不能为空")
    private Long productId;

    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    @Size(max = 255, message = "留言最多 255 字")
    private String remark;
}
