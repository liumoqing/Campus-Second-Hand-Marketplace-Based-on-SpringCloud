package com.campus.mall.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * U2 修改资料请求（03 §3）：全部可选，只传要改的。
 * username / role / status / canPublish 不允许通过本接口修改。
 */
@Data
public class ProfileUpdateDTO {

    @Size(min = 2, max = 20, message = "昵称长度 2~20")
    private String nickname;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    private Integer gender;

    @Size(max = 200, message = "个性签名最多 200 字")
    private String signature;

    @Size(max = 32)
    private String province;

    @Size(max = 32)
    private String city;

    @Size(max = 32)
    private String district;

    @Email(message = "邮箱格式不正确")
    @Size(max = 64)
    private String email;

    @Size(max = 20)
    private String phone;
}
