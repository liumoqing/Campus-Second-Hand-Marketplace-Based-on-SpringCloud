package com.campus.mall.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetCodeDTO {

    @NotBlank(message = "请输入注册邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;
}
