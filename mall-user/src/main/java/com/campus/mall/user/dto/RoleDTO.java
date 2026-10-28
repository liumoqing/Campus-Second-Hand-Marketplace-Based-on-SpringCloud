package com.campus.mall.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * U17 设置角色请求（仅 SUPER_ADMIN）：只允许 USER / ADMIN。
 */
@Data
public class RoleDTO {

    @NotBlank(message = "role 不能为空")
    private String role;
}
