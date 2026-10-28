package com.campus.mall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 内部注册（03 §8 POST /inner/user）：auth 调用，落库恒为 USER / NORMAL / can_publish=1，不接受 role。
 */
@Data
public class RegisterUserDTO {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^\\w{4,20}$", message = "用户名 4~20 位字母数字下划线")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 64)
    private String email;

    /** 已在前端 BCrypt 之前的明文，落库前由本服务加密 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码 8~20 位")
    private String password;

    @Size(max = 20)
    private String nickname;

    @Size(max = 255)
    private String avatar;
}
