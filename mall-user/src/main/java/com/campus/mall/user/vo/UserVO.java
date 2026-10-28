package com.campus.mall.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * U1 我的资料（03 §3）。手机号脱敏，绝不返回 password。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private Integer gender;
    private String province;
    private String city;
    private String district;
    private String signature;
    private Integer creditScore;
    private String role;
    private String status;
    private Boolean canPublish;
    private LocalDateTime publishBanUntil;
    private LocalDateTime banUntil;
    private String banReason;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}
