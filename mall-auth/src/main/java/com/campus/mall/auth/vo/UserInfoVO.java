package com.campus.mall.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * A3/A4/A6 的 userInfo（03 §2）：前端登录态与"上架权限闸门"都读这里。
 */
@Data
public class UserInfoVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private String city;
    private String signature;
    private String role;
    private String status;
    private Boolean canPublish;
    private LocalDateTime publishBanUntil;
    private Integer creditScore;
    private LocalDateTime lastLoginTime;
}
