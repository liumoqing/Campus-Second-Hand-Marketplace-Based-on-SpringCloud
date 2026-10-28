package com.campus.mall.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * U14 封禁请求（03 §3）：banUntil 与 permanent 二选一。
 */
@Data
public class BanDTO {

    private LocalDateTime banUntil;

    /** true = 永久封禁，服务端写 9999-12-31 23:59:59 */
    private Boolean permanent;

    @Size(max = 200, message = "封禁原因最多 200 字")
    private String reason;
}
