package com.campus.mall.auth.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.campus.mall.auth.client.UserClient;
import com.campus.mall.auth.dto.ChangePasswordDTO;
import com.campus.mall.auth.dto.LoginDTO;
import com.campus.mall.auth.dto.RegisterDTO;
import com.campus.mall.auth.dto.ResetPasswordDTO;
import com.campus.mall.auth.util.JwtUtil;
import com.campus.mall.auth.vo.LoginVO;
import com.campus.mall.auth.vo.UserInfoVO;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.R;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.storage.LocalFileStorage;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * A2~A10（03 §2）：auth 不建库，一切用户数据经 mall-user 内部接口读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern PASSWORD_RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,20}$");
    private static final Pattern USERNAME_RULE = Pattern.compile("^\\w{4,20}$");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration TV_TTL = Duration.ofDays(7);

    private final UserClient userClient;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;
    private final StringRedisTemplate redis;
    private final LocalFileStorage storage;

    @Value("${mall.reset.code-echo:true}")
    private boolean resetCodeEcho;

    @Value("${mall.reset.expire-seconds:600}")
    private long resetExpireSeconds;

    // ---------------- A2 注册 ----------------

    public Long register(RegisterDTO dto) {
        captchaService.verify(dto.getCaptchaKey(), dto.getCaptchaCode());
        if (!USERNAME_RULE.matcher(dto.getUsername()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名需 4~20 位字母数字下划线");
        }
        if (!PASSWORD_RULE.matcher(dto.getPassword()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码 8~20 位且必须同时包含字母和数字");
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR, "两次输入的密码不一致");
        }
        if (!StringUtils.hasText(dto.getAvatar()) || !isLocalAvatar(dto.getAvatar())) {
            throw new BizException(ErrorCode.AVATAR_INVALID);
        }

        UserClient.RegisterPayload payload = new UserClient.RegisterPayload();
        payload.setUsername(dto.getUsername());
        payload.setEmail(dto.getEmail());
        payload.setPassword(dto.getPassword());
        payload.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        payload.setAvatar(dto.getAvatar());

        R<Long> r = userClient.register(payload);
        if (r == null) {
            throw new BizException(ErrorCode.SERVICE_DEGRADED, "注册失败");
        }
        if (r.getCode() != 0) {
            // 透传 mall-user 的业务码，尤其是 20001/20006
            throw new BizException(r.getCode(), r.getMessage());
        }
        return r.getData();
    }

    private boolean isLocalAvatar(String avatar) {
        String a = avatar.trim();
        return a.startsWith("/files/avatar/")
                || a.startsWith("/static/avatar/")
                || a.matches("^https?://(127\\.0\\.0\\.1|localhost):4402/files/avatar/.*")
                || a.matches("^https?://(127\\.0\\.0\\.1|localhost):5173/static/avatar/.*");
    }

    // ---------------- A3 登录 ----------------

    public LoginVO login(LoginDTO dto) {
        captchaService.verify(dto.getCaptchaKey(), dto.getCaptchaCode());
        String account = dto.getAccount().trim();
        UserClient.Credential c = credential(account);
        if (c == null || !BCrypt.checkpw(dto.getPassword(), c.getPassword())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        checkBanned(c);
        return issue(c);
    }

    // ---------------- A4 刷新 ----------------

    public LoginVO refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtil.parse(refreshToken);
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!"refresh".equals(claims.get(JwtUtil.CLAIM_TYPE, String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String jti = claims.getId();
        String refreshKey = MallConstants.CACHE_REFRESH_TOKEN + jti;
        String uid = redis.opsForValue().get(refreshKey);
        if (uid == null) {
            // 一次性轮换：用过的 refreshToken 立即失效
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        redis.delete(refreshKey);

        Long userId = Long.valueOf(claims.get(JwtUtil.CLAIM_UID, Number.class).longValue());
        int tv = claims.get(JwtUtil.CLAIM_TV, Number.class).intValue();
        String cachedTv = redis.opsForValue().get(MallConstants.CACHE_USER_TV + userId);
        if (cachedTv != null && !cachedTv.equals(String.valueOf(tv))) {
            // 被封禁/被改角色：旧 tv 已作废，必须重新登录
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserInfoVO info = userInfo(userId);
        if (info == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return build(userId, info.getRole(), tv, info);
    }

    // ---------------- A5 退出 ----------------

    public void logout(String authorization, String refreshToken) {
        String access = stripBearer(authorization);
        if (StringUtils.hasText(access)) {
            try {
                Claims claims = jwtUtil.parse(access);
                String jti = claims.getId();
                long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
                if (ttlMs > 0) {
                    redis.opsForValue().set(MallConstants.CACHE_REFRESH_TOKEN + "black:" + jti, "1",
                            Duration.ofMillis(ttlMs));
                }
            } catch (Exception ignored) {
                // token 已过期也允许退出
            }
        }
        if (StringUtils.hasText(refreshToken)) {
            try {
                Claims c = jwtUtil.parse(refreshToken);
                redis.delete(MallConstants.CACHE_REFRESH_TOKEN + c.getId());
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    // ---------------- A6 当前登录用户 ----------------

    public UserInfoVO me(Long userId) {
        UserInfoVO vo = userInfo(userId);
        if (vo == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return vo;
    }

    // ---------------- A7 改密 ----------------

    public void changePassword(Long userId, ChangePasswordDTO dto) {
        if (!PASSWORD_RULE.matcher(dto.getNewPassword()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新密码 8~20 位且必须同时包含字母和数字");
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("oldPassword", dto.getOldPassword());
        body.put("password", dto.getNewPassword());
        R<Boolean> r = userClient.updatePassword(userId, body);
        if (r == null || r.getCode() != 0) {
            throw r == null ? new BizException(ErrorCode.SERVICE_DEGRADED, "改密失败")
                    : new BizException(fromCode(r.getCode()), r.getMessage());
        }
        redis.delete(MallConstants.CACHE_USER_TV + userId);
    }

    private static ErrorCode fromCode(int code) {
        for (ErrorCode e : ErrorCode.values()) {
            if (e.getCode() == code) {
                return e;
            }
        }
        return ErrorCode.SERVICE_DEGRADED;
    }

    // ---------------- A8 注册头像上传 ----------------

    public String uploadAvatar(MultipartFile file) {
        return storage.save(file, "avatar", 2);
    }

    // ---------------- A9 发重置码 ----------------

    public Map<String, Object> resetCode(String email) {
        String limitKey = "mall:auth:reset:limit:" + email;
        // SET NX EX 建窗口；不要用 RedisTemplate.expire(...)，SDR 4.x 的 DefaultedRedisConnection#pExpire 会自递归
        Boolean first = redis.opsForValue().setIfAbsent(limitKey, "1", Duration.ofHours(1));
        Long times = Boolean.TRUE.equals(first) ? 1L : redis.opsForValue().increment(limitKey);
        if (times != null && times > 3) {
            throw new BizException(ErrorCode.SERVICE_DEGRADED, "该邮箱 1 小时内最多申请 3 次");
        }

        String code = RandomUtil.randomNumbers(6);
        // 邮箱不存在也不报错，避免探测已注册邮箱；但只在存在时写码
        UserClient.Credential c = credential(email);
        if (c != null) {
            redis.opsForValue().set(MallConstants.CACHE_AUTH_RESET + email, code,
                    Duration.ofSeconds(resetExpireSeconds));
            log.info("找回密码验证码已生成：email={}, code={}（开发模式回显）", email, code);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sent", true);
        data.put("expireSeconds", resetExpireSeconds);
        if (resetCodeEcho && c != null) {
            data.put("devCode", code);
        }
        return data;
    }

    // ---------------- A10 用验证码重置 ----------------

    public boolean resetPassword(ResetPasswordDTO dto) {
        if (!PASSWORD_RULE.matcher(dto.getNewPassword()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新密码 8~20 位且必须同时包含字母和数字");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR, "两次输入的密码不一致");
        }
        String key = MallConstants.CACHE_AUTH_RESET + dto.getEmail();
        String expect = redis.opsForValue().get(key);
        if (expect == null || !expect.equals(dto.getCode())) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID, "验证码错误或已过期");
        }
        UserClient.Credential c = credential(dto.getEmail());
        if (c == null) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID, "验证码错误或已过期");
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("password", dto.getNewPassword());
        R<Boolean> r = userClient.updatePassword(c.getId(), body);
        if (r == null || r.getCode() != 0) {
            throw new BizException(ErrorCode.SERVICE_DEGRADED, "密码重置失败");
        }
        redis.delete(key);
        // 改密后 token_version 已 +1，旧 token 全部失效
        return true;
    }

    // ---------------- 内部工具 ----------------

    private UserClient.Credential credential(String account) {
        try {
            R<UserClient.Credential> r = userClient.credential(account);
            return r == null ? null : r.getData();
        } catch (Exception e) {
            log.error("调用 mall-user 凭证接口失败：account={}", account, e);
            throw new BizException(ErrorCode.SERVICE_DEGRADED, "账号服务暂不可用");
        }
    }

    private void checkBanned(UserClient.Credential c) {
        if (!MallConstants.STATUS_BANNED.equals(c.getStatus())) {
            return;
        }
        LocalDateTime until = c.getBanUntil();
        if (until != null && until.isBefore(LocalDateTime.now())) {
            // 已到期，定时任务会改状态，这里先放行
            log.info("登录时发现封禁已到期，放行：userId={}", c.getId());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("banUntil", until);
        data.put("reason", c.getBanReason());
        String time = until == null ? "" : until.format(TIME_FMT);
        throw new BizException(ErrorCode.ACCOUNT_BANNED,
                "您因违反用户规则已被封禁，解封时间" + time, data);
    }

    private LoginVO issue(UserClient.Credential c) {
        int tv = c.getTokenVersion() == null ? 0 : c.getTokenVersion();
        redis.opsForValue().set(MallConstants.CACHE_USER_TV + c.getId(), String.valueOf(tv), TV_TTL);
        redis.delete(MallConstants.CACHE_USER_BANNED + c.getId());
        try {
            userClient.touchLoginTime(c.getId());
        } catch (Exception e) {
            log.warn("刷新最后登录时间失败：userId={}", c.getId());
        }
        UserInfoVO info = new UserInfoVO();
        info.setId(c.getId());
        info.setUsername(c.getUsername());
        info.setNickname(c.getNickname());
        info.setAvatar(c.getAvatar());
        info.setEmail(c.getEmail());
        info.setRole(c.getRole());
        info.setStatus(c.getStatus());
        info.setCanPublish(c.getCanPublish());
        info.setPublishBanUntil(c.getPublishBanUntil());
        return build(c.getId(), c.getRole(), tv, info);
    }

    private LoginVO build(Long userId, String role, int tv, UserInfoVO info) {
        LoginVO vo = new LoginVO();
        vo.setAccessToken(jwtUtil.accessToken(userId, role, tv));
        String refresh = jwtUtil.refreshToken(userId, tv);
        Claims claims = jwtUtil.parse(refresh);
        redis.opsForValue().set(MallConstants.CACHE_REFRESH_TOKEN + claims.getId(),
                String.valueOf(userId), jwtUtil.getRefreshTtl());
        vo.setRefreshToken(refresh);
        vo.setExpiresIn(jwtUtil.accessSeconds());
        vo.setUserInfo(info);
        return vo;
    }

    private UserInfoVO userInfo(Long userId) {
        R<Map<String, Object>> r = userClient.brief(userId);
        if (r == null || r.getData() == null) {
            return null;
        }
        Map<String, Object> m = r.getData();
        UserInfoVO vo = new UserInfoVO();
        vo.setId(userId);
        vo.setUsername(str(m.get("username")));
        vo.setNickname(str(m.get("nickname")));
        vo.setAvatar(str(m.get("avatar")));
        vo.setEmail(str(m.get("email")));
        vo.setPhone(str(m.get("phone")));
        vo.setProvince(str(m.get("province")));
        vo.setCity(str(m.get("city")));
        vo.setDistrict(str(m.get("district")));
        vo.setSignature(str(m.get("signature")));
        vo.setRole(str(m.get("role")));
        vo.setStatus(str(m.get("status")));
        Object canPublish = m.get("canPublish");
        vo.setCanPublish(canPublish instanceof Boolean b ? b : Boolean.TRUE.equals(canPublish));
        vo.setCreditScore(m.get("creditScore") instanceof Number n ? n.intValue() : 100);
        return vo;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String stripBearer(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        return authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    }
}
