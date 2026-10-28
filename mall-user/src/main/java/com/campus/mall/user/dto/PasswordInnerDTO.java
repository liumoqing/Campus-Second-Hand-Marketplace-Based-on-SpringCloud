package com.campus.mall.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 内部改密（03 §8 U23 PUT /inner/user/{id}/password）：auth 的 A7 / A10 调用。
 * <p>
 * 带 {@code oldPassword} 表示用户自己在改密（A7），必须校验原密码；
 * 不带表示凭邮箱验证码重置（A10），由 auth 侧完成身份校验。
 */
@Data
public class PasswordInnerDTO {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "密码 8~20 位")
    private String password;

    /** A7 用户改密时必传；A10 找回密码时不传 */
    private String oldPassword;
}
