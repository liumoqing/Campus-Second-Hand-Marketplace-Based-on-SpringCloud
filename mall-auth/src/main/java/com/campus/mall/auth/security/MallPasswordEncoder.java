package com.campus.mall.auth.security;

import cn.hutool.crypto.digest.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 兼容 mall-user 中已有的 Hutool BCrypt 密码哈希；同时支持 OAuth2 client 的 {noop} 密钥。 */
public class MallPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        return BCrypt.hashpw(rawPassword.toString(), BCrypt.gensalt());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith("{noop}")) {
            return rawPassword.toString().equals(encodedPassword.substring("{noop}".length()));
        }
        return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return false;
    }
}