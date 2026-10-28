package com.campus.mall.user.controller;

import com.campus.mall.common.api.R;
import com.campus.mall.user.dto.PasswordInnerDTO;
import com.campus.mall.user.dto.RegisterUserDTO;
import com.campus.mall.user.entity.UserAddress;
import com.campus.mall.user.service.InnerUserService;
import com.campus.mall.user.vo.CredentialVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 03 §8 mall-user 内部接口（均需 X-Internal-Token，网关不路由 /inner/**）。
 */
@RestController
@RequestMapping("/inner/user")
@RequiredArgsConstructor
public class InnerUserController {

    private final InnerUserService innerUserService;

    /** U18 凭证（auth 登录用，account = 用户名或邮箱） */
    @GetMapping("/credential")
    public R<CredentialVO> credential(@RequestParam("account") String account) {
        return R.ok(innerUserService.credential(account));
    }

    /** U19 注册落库 */
    @PostMapping
    public R<Long> register(@RequestBody @Valid RegisterUserDTO dto) {
        return R.ok(innerUserService.register(dto));
    }

    /** U23 改密（A7 / A10 调用，自带 token_version+1） */
    @PutMapping("/{id:\\d+}/password")
    public R<Boolean> updatePassword(@PathVariable Long id, @RequestBody @Valid PasswordInnerDTO dto) {
        return R.ok(innerUserService.updatePassword(id, dto.getOldPassword(), dto.getPassword()));
    }

    /** 令牌版本 +1（改角色/踢下线等场景由 auth 主动调） */
    @PutMapping("/{id:\\d+}/token-version")
    public R<Integer> bumpTokenVersion(@PathVariable Long id) {
        return R.ok(innerUserService.bumpTokenVersion(id));
    }

    /** 登录成功后刷新最后登录时间 */
    @PutMapping("/{id:\\d+}/login-time")
    public R<Boolean> touchLoginTime(@PathVariable Long id) {
        return R.ok(innerUserService.touchLoginTime(id));
    }

    /** 简要用户信息（商品/订单/消息服务用） */
    @GetMapping("/{id:\\d+}")
    public R<Map<String, Object>> brief(@PathVariable Long id) {
        return R.ok(innerUserService.brief(id));
    }

    /** 批量简要信息（订单列表一次取卖家昵称，避免 N+1） */
    @GetMapping("/batch")
    public R<List<Map<String, Object>>> batch(@RequestParam("ids") List<Long> ids) {
        return R.ok(innerUserService.batch(ids));
    }

    /** 下单取完整地址（含真实手机号，用于订单快照） */
    @GetMapping("/address/{addressId:\\d+}")
    public R<UserAddress> address(@PathVariable Long addressId, @RequestParam("userId") Long userId) {
        return R.ok(innerUserService.addressForOrder(userId, addressId));
    }

    /** 信用分扣减（mall-order 评价 ≤2 星 / 退款时调用） */
    @PutMapping("/{id:\\d+}/credit")
    public R<Integer> deductCredit(@PathVariable Long id, @RequestParam("score") int score) {
        return R.ok(innerUserService.deductCredit(id, score));
    }
}
