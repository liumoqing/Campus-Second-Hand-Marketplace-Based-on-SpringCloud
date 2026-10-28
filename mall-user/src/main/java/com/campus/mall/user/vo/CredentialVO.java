package com.campus.mall.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内部凭证（03 §8 GET /inner/user/credential?account=）：只给 mall-auth 用，
 * 含 BCrypt 密文与封禁/上架权限/令牌版本，绝对不过网关。
 */
@Data
public class CredentialVO {

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private String role;
    private String status;
    private Integer tokenVersion;
    private LocalDateTime banUntil;
    private String banReason;
    private Boolean canPublish;
    private LocalDateTime publishBanUntil;
}
