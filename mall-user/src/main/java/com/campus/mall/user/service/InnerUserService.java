package com.campus.mall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.crypto.digest.BCrypt;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.user.dto.RegisterUserDTO;
import com.campus.mall.user.entity.User;
import com.campus.mall.user.entity.UserAddress;
import com.campus.mall.user.mapper.UserMapper;
import com.campus.mall.user.vo.CredentialVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部接口（03 §8 mall-user）：只被其它服务用 internal-token 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InnerUserService {

    private final UserMapper userMapper;
    private final AddressService addressService;

    public CredentialVO credential(String account) {
        if (account == null || account.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "account 不能为空");
        }
        User u = account.contains("@")
                ? userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, account).last("LIMIT 1"))
                : userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, account).last("LIMIT 1"));
        if (u == null) {
            // 不区分"用户不存在"与"密码错"，由 auth 统一返回 20002
            return null;
        }
        CredentialVO vo = new CredentialVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setPassword(u.getPassword());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setEmail(u.getEmail());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        vo.setTokenVersion(u.getTokenVersion() == null ? 0 : u.getTokenVersion());
        vo.setBanUntil(u.getBanUntil());
        vo.setBanReason(u.getBanReason());
        vo.setCanPublish(u.getCanPublish() != null && u.getCanPublish() == 1);
        vo.setPublishBanUntil(u.getPublishBanUntil());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterUserDTO dto) {
        if (countByUsername(dto.getUsername()) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        if (countByEmail(dto.getEmail()) > 0) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }
        User u = new User();
        u.setUsername(dto.getUsername());
        u.setEmail(dto.getEmail());
        u.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        u.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername() : dto.getNickname());
        u.setAvatar(dto.getAvatar() == null ? "" : dto.getAvatar());
        u.setGender(0);
        u.setCreditScore(100);
        // 三条硬约束：注册永远是 USER / NORMAL / 可上架，role 参数一律忽略
        u.setRole(MallConstants.ROLE_USER);
        u.setStatus(MallConstants.STATUS_NORMAL);
        u.setCanPublish(1);
        u.setTokenVersion(0);
        userMapper.insert(u);
        log.info("注册成功：userId={}, username={}", u.getId(), u.getUsername());
        return u.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updatePassword(Long id, String oldPassword, String rawPassword) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // A7 用户自己改密：必须校验原密码；A10 找回密码由 auth 的验证码兜底
        if (oldPassword != null && !oldPassword.isBlank()
                && !BCrypt.checkpw(oldPassword, u.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR, "原密码不正确");
        }
        u.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
        u.setTokenVersion((u.getTokenVersion() == null ? 0 : u.getTokenVersion()) + 1);
        userMapper.updateById(u);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer bumpTokenVersion(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        u.setTokenVersion((u.getTokenVersion() == null ? 0 : u.getTokenVersion()) + 1);
        userMapper.updateById(u);
        return u.getTokenVersion();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean touchLoginTime(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        u.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(u);
        return true;
    }

    /** 信用分扣减（01 BR-23）：评价 ≤2 星扣 2、退款扣 1，lower bound 0，由订单服务调用。 */
    public int deductCredit(Long userId, int score) {
        if (score <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "扣减分值必须大于 0");
        }
        return userMapper.deductCredit(userId, score);
    }

    /** 商品/订单/消息服务取卖家或对方简要信息（不做脱敏，内部用）。 */
    public Map<String, Object> brief(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("avatar", u.getAvatar());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("gender", u.getGender());
        m.put("province", u.getProvince());
        m.put("city", u.getCity());
        m.put("district", u.getDistrict());
        m.put("signature", u.getSignature());
        m.put("creditScore", u.getCreditScore());
        m.put("role", u.getRole());
        m.put("status", u.getStatus());
        m.put("banUntil", u.getBanUntil());
        m.put("canPublish", u.getCanPublish() != null && u.getCanPublish() == 1);
        m.put("publishBanUntil", u.getPublishBanUntil());
        m.put("createTime", u.getCreateTime());
        m.put("lastLoginTime", u.getLastLoginTime());
        return m;
    }

    public List<Map<String, Object>> batch(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("city", u.getCity());
            return m;
        }).toList();
    }

    /** 下单时取完整地址（订单要写快照，不能给脱敏后的手机号）。 */
    public UserAddress addressForOrder(Long userId, Long addressId) {
        return addressService.requireOwn(userId, addressId);
    }

    private long countByUsername(String username) {
        Long c = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        return c == null ? 0 : c;
    }

    private long countByEmail(String email) {
        Long c = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        return c == null ? 0 : c;
    }
}
