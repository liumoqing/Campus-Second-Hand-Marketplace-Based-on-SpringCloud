package com.campus.mall.user.vo;

import lombok.Data;

/**
 * U5 ~ U10 地址（03 §3）：手机号脱敏。
 */
@Data
public class AddressVO {

    private Long id;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private Boolean isDefault;
}
