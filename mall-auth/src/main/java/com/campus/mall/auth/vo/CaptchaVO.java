package com.campus.mall.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * A1 图形验证码（03 §2）：imageBase64 自带 data URI 前缀，前端直接放 img src。
 */
@Data
@AllArgsConstructor
public class CaptchaVO {

    private String captchaKey;
    private String imageBase64;
}
