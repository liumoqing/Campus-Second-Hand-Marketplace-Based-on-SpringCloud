package com.campus.mall.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.mall.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址（02 §5.2 t_user_address）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_address")
public class UserAddress extends BaseEntity {

    private Long userId;
    private String receiver;
    private String phone;
    private String province;
    private String city;
    private String district;
    private String detail;
    /** 1 默认 0 非默认 */
    private Integer isDefault;
}
