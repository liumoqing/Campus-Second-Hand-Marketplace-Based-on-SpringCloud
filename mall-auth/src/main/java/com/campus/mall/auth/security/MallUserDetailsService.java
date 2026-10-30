package com.campus.mall.auth.security;

import com.campus.mall.auth.client.UserClient;
import com.campus.mall.common.api.R;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 从 mall-user 加载登录凭证，供 Spring Security 表单登录与 OAuth2 授权码流程使用。 */
@Service
@RequiredArgsConstructor
public class MallUserDetailsService implements UserDetailsService {

    private final UserClient userClient;

    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        R<UserClient.Credential> response = userClient.credential(account);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new UsernameNotFoundException("用户名/邮箱或密码错误");
        }
        UserClient.Credential c = response.getData();
        boolean banned = "BANNED".equalsIgnoreCase(c.getStatus())
                && (c.getBanUntil() == null || c.getBanUntil().isAfter(LocalDateTime.now()));
        String role = c.getRole() == null ? "USER" : c.getRole();
        int tokenVersion = c.getTokenVersion() == null ? 0 : c.getTokenVersion();
        return new MallUserDetails(c.getId(), c.getUsername(), c.getPassword(), role, tokenVersion, !banned);
    }
}