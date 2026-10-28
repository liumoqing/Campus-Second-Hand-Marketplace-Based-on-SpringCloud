package com.campus.mall.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * U16 上架权限请求（03 §3）：canPublish=true 表示恢复。
 */
@Data
public class PublishPermissionDTO {

    @NotNull(message = "canPublish 不能为空")
    private Boolean canPublish;

    private LocalDateTime publishBanUntil;

    /** 永久限制上架：写 9999-12-31 23:59:59 */
    private Boolean permanent;
}
