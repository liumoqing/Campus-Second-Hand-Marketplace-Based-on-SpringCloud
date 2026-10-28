package com.campus.mall.auth.vo;

import lombok.Data;

/**
 * A3/A4 登录响应（03 §2）。
 */
@Data
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private UserInfoVO userInfo;
}
