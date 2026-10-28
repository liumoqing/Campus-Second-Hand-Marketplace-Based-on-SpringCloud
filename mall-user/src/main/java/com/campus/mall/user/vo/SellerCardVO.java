package com.campus.mall.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * U3 卖家主页 / 详情页卖家卡片（03 §3，7 个展示位一次给全）。
 */
@Data
public class SellerCardVO {

    private Long id;
    private String nickname;
    private String avatar;
    private String city;
    private String signature;
    private Integer creditScore;
    private Double avgRating;
    private Long reviewCount;
    private Long soldCount;
    private Long onSaleCount;
    private LocalDateTime joinedAt;
    private Long registerDays;
    private LocalDateTime lastLoginTime;
}
