package com.campus.mall.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * U6/U7 收货地址请求（03 §3）：省市区三级必须齐全。
 */
@Data
public class AddressDTO {

    @NotBlank(message = "收货人不能为空")
    @Size(min = 2, max = 20, message = "收货人 2~20 字")
    private String receiver;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "省不能为空")
    private String province;

    @NotBlank(message = "市不能为空")
    private String city;

    @NotBlank(message = "区县不能为空")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(min = 5, max = 100, message = "详细地址 5~100 字")
    private String detail;

    private Boolean isDefault;
}
