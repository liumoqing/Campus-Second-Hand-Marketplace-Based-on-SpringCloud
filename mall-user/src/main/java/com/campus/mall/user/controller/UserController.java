package com.campus.mall.user.controller;

import com.campus.mall.common.api.R;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.user.dto.ProfileUpdateDTO;
import com.campus.mall.user.service.UserService;
import com.campus.mall.user.vo.MyStatisticsVO;
import com.campus.mall.user.vo.SellerCardVO;
import com.campus.mall.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 03 §3 U1~U4、U11。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** U1 我的资料 */
    @GetMapping("/profile")
    @RequireRole
    public R<UserVO> profile() {
        return R.ok(userService.profile(UserContext.requireUserId()));
    }

    /** U2 修改资料 */
    @PutMapping("/profile")
    @RequireRole
    public R<Boolean> updateProfile(@RequestBody @Valid ProfileUpdateDTO dto) {
        return R.ok(userService.updateProfile(UserContext.requireUserId(), dto));
    }

    /** U3 卖家主页（登录可见） */
    @GetMapping("/{id:\\d+}")
    @RequireRole
    public R<SellerCardVO> sellerCard(@PathVariable Long id) {
        return R.ok(userService.sellerCard(id));
    }

    /** U4 我的统计 */
    @GetMapping("/statistics")
    @RequireRole
    public R<MyStatisticsVO> statistics() {
        return R.ok(userService.statistics(UserContext.requireUserId()));
    }

    /** U11 枚举字典 */
    @GetMapping("/dict")
    @RequireRole
    public R<Map<String, List<Map<String, Object>>>> dict() {
        return R.ok(userService.dict());
    }
}
