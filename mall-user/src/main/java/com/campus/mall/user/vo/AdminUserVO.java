package com.campus.mall.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * U12 后台用户列表行（03 §3）。不含商品计数（避免 N+1，进 U13 看）。
 */
@Data
public class AdminUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private String role;
    private String status;
    private LocalDateTime banUntil;
    private String banReason;
    private Boolean canPublish;
    private LocalDateTime publishBanUntil;
    private Integer creditScore;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}
