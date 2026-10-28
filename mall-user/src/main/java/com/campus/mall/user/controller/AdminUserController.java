package com.campus.mall.user.controller;

import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.user.dto.AdminUserQuery;
import com.campus.mall.user.dto.BanDTO;
import com.campus.mall.user.dto.PublishPermissionDTO;
import com.campus.mall.user.dto.RoleDTO;
import com.campus.mall.user.service.AdminUserService;
import com.campus.mall.user.vo.AdminUserDetailVO;
import com.campus.mall.user.vo.AdminUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 03 §3 U12~U17 后台用户治理。
 * U17 只有 SUPER_ADMIN 能调：网关已按角色拦，这里再兜一层（10003）。
 */
@RestController
@RequestMapping("/user/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/page")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<PageResult<AdminUserVO>> page(AdminUserQuery query) {
        return R.ok(adminUserService.page(query));
    }

    @GetMapping("/{id:\\d+}")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<AdminUserDetailVO> detail(@PathVariable Long id) {
        return R.ok(adminUserService.detail(id));
    }

    @PutMapping("/{id:\\d+}/ban")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<Map<String, Object>> ban(@PathVariable Long id, @RequestBody @Valid BanDTO dto) {
        return R.ok(adminUserService.ban(UserContext.requireUserId(), id, dto));
    }

    @PutMapping("/{id:\\d+}/unban")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<Object> unban(@PathVariable Long id) {
        return R.ok(adminUserService.unban(UserContext.requireUserId(), id));
    }

    @PutMapping("/{id:\\d+}/publish-permission")
    @RequireRole(MallConstants.ROLE_ADMIN)
    public R<Map<String, Object>> publishPermission(@PathVariable Long id,
                                                   @RequestBody @Valid PublishPermissionDTO dto) {
        return R.ok(adminUserService.publishPermission(UserContext.requireUserId(), id, dto));
    }

    @PutMapping("/{id:\\d+}/role")
    @RequireRole(MallConstants.ROLE_SUPER_ADMIN)
    public R<Map<String, Object>> setRole(@PathVariable Long id, @RequestBody @Valid RoleDTO dto) {
        if (!UserContext.isSuperAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return R.ok(adminUserService.setRole(UserContext.requireUserId(), id, dto.getRole()));
    }
}
