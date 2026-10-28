package com.campus.mall.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * U13 用户详情（U1 结构 + 商品/订单统计），供后台详情页。
 */
@Data
public class AdminUserDetailVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer gender;
    private String province;
    private String city;
    private String district;
    private String signature;
    private Integer creditScore;
    private String role;
    private String status;
    private LocalDateTime banUntil;
    private String banReason;
    private Boolean canPublish;
    private LocalDateTime publishBanUntil;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;

    private Long onSaleCount = 0L;
    private Long draftCount = 0L;
    private Long soldCount = 0L;
    private Long boughtCount = 0L;
    private Double avgRating = 0D;
    private Long reviewCount = 0L;
}
