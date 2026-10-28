package com.campus.mall.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.mall.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户（02 §5.1 t_user）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    /** 0未知 1男 2女 */
    private Integer gender;
    private String province;
    private String city;
    private String district;
    private String signature;
    private Integer creditScore;
    /** USER / ADMIN / SUPER_ADMIN */
    private String role;
    /** NORMAL / BANNED */
    private String status;
    private LocalDateTime banUntil;
    private String banReason;
    /** 1 可上架 0 被限制 */
    private Integer canPublish;
    private LocalDateTime publishBanUntil;
    private Integer tokenVersion;
    private LocalDateTime lastLoginTime;
}
