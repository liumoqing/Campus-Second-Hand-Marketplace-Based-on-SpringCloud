package com.campus.mall.auth.client;

import com.campus.mall.common.api.R;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * mall-user 内部接口（03 §8）。auth 不建库，凭证唯一来源是 mall-user。
 */
@FeignClient(name = "mall-user", path = "/inner/user")
public interface UserClient {

    @GetMapping("/credential")
    R<Credential> credential(@RequestParam("account") String account);

    @PostMapping
    R<Long> register(@RequestBody RegisterPayload payload);

    @PutMapping("/{id}/password")
    R<Boolean> updatePassword(@PathVariable("id") Long id, @RequestBody Map<String, String> body);

    @PutMapping("/{id}/login-time")
    R<Boolean> touchLoginTime(@PathVariable("id") Long id);

    @PutMapping("/{id}/token-version")
    R<Integer> bumpTokenVersion(@PathVariable("id") Long id);

    @GetMapping("/{id}")
    R<Map<String, Object>> brief(@PathVariable("id") Long id);

    @Data
    class Credential {
        private Long id;
        private String username;
        private String password;
        private String nickname;
        private String avatar;
        private String email;
        private String role;
        private String status;
        private Integer tokenVersion;
        private LocalDateTime banUntil;
        private String banReason;
        private Boolean canPublish;
        private LocalDateTime publishBanUntil;
    }

    @Data
    class RegisterPayload {
        private String username;
        private String email;
        private String password;
        private String nickname;
        private String avatar;
    }
}
