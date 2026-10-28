package com.campus.mall.user.controller;

import com.campus.mall.common.api.R;
import com.campus.mall.common.security.RequireRole;
import com.campus.mall.common.security.UserContext;
import com.campus.mall.user.dto.AddressDTO;
import com.campus.mall.user.service.AddressService;
import com.campus.mall.user.vo.AddressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 03 §3 U5~U10 收货地址。
 */
@RestController
@RequestMapping("/user/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @RequireRole
    public R<List<AddressVO>> list() {
        return R.ok(addressService.list(UserContext.requireUserId()));
    }

    @PostMapping
    @RequireRole
    public R<Map<String, Object>> create(@RequestBody @Valid AddressDTO dto) {
        // U6 响应 data: { id }（03 §3 U6）
        return R.ok(Map.of("id", addressService.create(UserContext.requireUserId(), dto)));
    }

    @PutMapping("/{id:\\d+}")
    @RequireRole
    public R<Boolean> update(@PathVariable Long id, @RequestBody @Valid AddressDTO dto) {
        return R.ok(addressService.update(UserContext.requireUserId(), id, dto));
    }

    @DeleteMapping("/{id:\\d+}")
    @RequireRole
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(addressService.delete(UserContext.requireUserId(), id));
    }

    @PutMapping("/{id:\\d+}/default")
    @RequireRole
    public R<Boolean> setDefault(@PathVariable Long id) {
        return R.ok(addressService.setDefault(UserContext.requireUserId(), id));
    }

    @GetMapping("/default")
    @RequireRole
    public R<AddressVO> getDefault() {
        return R.ok(addressService.getDefault(UserContext.requireUserId()));
    }
}
