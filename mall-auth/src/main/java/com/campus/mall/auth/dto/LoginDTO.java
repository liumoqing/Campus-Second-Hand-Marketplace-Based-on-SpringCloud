package com.campus.mall.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * A3 登录：account 可以是用户名或邮箱（大小写不敏感）。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "请输入用户名或邮箱")
    private String account;

    @NotBlank(message = "请输入密码")
    private String password;

    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    @NotBlank(message = "请输入验证码")
    private String captchaCode;
}
