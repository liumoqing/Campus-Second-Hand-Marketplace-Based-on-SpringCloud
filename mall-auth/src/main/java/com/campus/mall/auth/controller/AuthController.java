package com.campus.mall.auth.controller;

import com.campus.mall.auth.dto.ChangePasswordDTO;
import com.campus.mall.auth.dto.LoginDTO;
import com.campus.mall.auth.dto.RefreshDTO;
import com.campus.mall.auth.dto.RegisterDTO;
import com.campus.mall.auth.dto.ResetCodeDTO;
import com.campus.mall.auth.dto.ResetPasswordDTO;
import com.campus.mall.auth.service.AuthService;
import com.campus.mall.auth.service.CaptchaService;
import com.campus.mall.auth.vo.CaptchaVO;
import com.campus.mall.auth.vo.LoginVO;
import com.campus.mall.auth.vo.UserInfoVO;
import com.campus.mall.common.api.R;
import com.campus.mall.common.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 认证中心 A1~A10（03 §2）。网关按 /auth/** 前缀 StripPrefix=1 转发到本服务。
 * <p>
 * 公开接口（A1/A2/A3/A4/A8/A9/A10）由网关白名单放行，其余需登录。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    /** A1 图形验证码 */
    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(captchaService.generate());
    }

    /** A2 注册（头像、用户名、邮箱、密码、确认密码、验证码） */
    @PostMapping("/register")
    public R<Long> register(@RequestBody @Valid RegisterDTO dto) {
        return R.ok("注册成功", authService.register(dto));
    }

    /** A3 登录（用户名或邮箱 + 密码 + 验证码） */
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /** A4 刷新令牌（一次性轮换） */
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@RequestBody @Valid RefreshDTO dto) {
        return R.ok(authService.refresh(dto.getRefreshToken()));
    }

    /** A5 退出登录 */
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                          @RequestBody(required = false) RefreshDTO dto) {
        authService.logout(authorization, dto == null ? null : dto.getRefreshToken());
        return R.ok();
    }

    /** A6 当前登录用户 */
    @GetMapping("/me")
    public R<UserInfoVO> me() {
        return R.ok(authService.me(UserContext.requireUserId()));
    }

    /** A7 修改密码 */
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody @Valid ChangePasswordDTO dto) {
        authService.changePassword(UserContext.requireUserId(), dto);
        return R.ok("密码已修改，请重新登录", null);
    }

    /** A8 注册头像上传（公开，注册前调用） */
    @PostMapping("/avatar")
    public R<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return R.ok(Map.of("url", authService.uploadAvatar(file)));
    }

    /** A9 发送找回密码验证码 */
    @PostMapping("/reset-code")
    public R<Map<String, Object>> resetCode(@RequestBody @Valid ResetCodeDTO dto) {
        return R.ok(authService.resetCode(dto.getEmail()));
    }

    /** A10 用验证码重置密码 */
    @PostMapping("/reset-password")
    public R<Boolean> resetPassword(@RequestBody @Valid ResetPasswordDTO dto) {
        return R.ok("密码已重置，请用新密码登录", authService.resetPassword(dto));
    }
}
