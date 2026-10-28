package com.campus.mall.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * A2 注册（03 §2）：头像、用户名、邮箱、密码、确认密码 + 验证码，共 6 项。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "请先上传头像")
    @Size(max = 255)
    private String avatar;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 64)
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;

    @Size(max = 20)
    private String nickname;

    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    @NotBlank(message = "请输入验证码")
    private String captchaCode;
}
