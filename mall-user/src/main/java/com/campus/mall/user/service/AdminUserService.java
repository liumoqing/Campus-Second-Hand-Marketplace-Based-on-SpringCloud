package com.campus.mall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.PageResult;
import com.campus.mall.common.api.R;
import com.campus.mall.common.constant.MallConstants;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.util.MaskUtil;
import com.campus.mall.user.client.OrderClient;
import com.campus.mall.user.client.ProductClient;
import com.campus.mall.user.dto.AdminUserQuery;
import com.campus.mall.user.dto.BanDTO;
import com.campus.mall.user.dto.PublishPermissionDTO;
import com.campus.mall.user.entity.User;
import com.campus.mall.user.mapper.UserMapper;
import com.campus.mall.user.vo.AdminUserDetailVO;
import com.campus.mall.user.vo.AdminUserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台用户治理（03 §3 U12~U17）：封禁 / 解封 / 上架权限 / 设置角色。
 * 三条硬约束：不能操作 SUPER_ADMIN（20008）、不能操作自己（20009）、角色只能 USER↔ADMIN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Duration PERMANENT_BAN_TTL = Duration.ofDays(7);

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OrderClient orderClient;
    private final ProductClient productClient;

    public PageResult<AdminUserVO> page(AdminUserQuery query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : Math.min(query.getPageSize(), 50);

        var wrapper = Wrappers.<User>lambdaQuery();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getEmail, kw)
                    .or().like(User::getNickname, kw)
                    .or().like(User::getPhone, kw));
        }
        if (query.getRole() != null && !query.getRole().isBlank()) {
            wrapper.eq(User::getRole, query.getRole());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        if (query.getCanPublish() != null) {
            wrapper.eq(User::getCanPublish, query.getCanPublish());
        }
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.from(page.convert(AdminUserService::toVO));
    }

    public AdminUserDetailVO detail(Long id) {
        User u = requireUser(id);
        AdminUserDetailVO vo = new AdminUserDetailVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setEmail(u.getEmail());
        vo.setPhone(MaskUtil.phone(u.getPhone()));
        vo.setGender(u.getGender());
        vo.setProvince(u.getProvince());
        vo.setCity(u.getCity());
        vo.setDistrict(u.getDistrict());
        vo.setSignature(u.getSignature());
        vo.setCreditScore(u.getCreditScore());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        vo.setBanUntil(u.getBanUntil());
        vo.setBanReason(u.getBanReason());
        vo.setCanPublish(u.getCanPublish() != null && u.getCanPublish() == 1);
        vo.setPublishBanUntil(u.getPublishBanUntil());
        vo.setLastLoginTime(u.getLastLoginTime());
        vo.setCreateTime(u.getCreateTime());

        try {
            R<ProductClient.ProductStatistics> ps = productClient.statistics(id);
            if (ps != null && ps.getData() != null) {
                vo.setOnSaleCount(ps.getData().getOnSaleCount());
                vo.setDraftCount(ps.getData().getDraftCount());
                vo.setSoldCount(ps.getData().getSoldCount());
            }
        } catch (Exception e) {
            log.warn("U13 商品统计降级：userId={}, {}", id, e.getMessage());
        }
        try {
            R<OrderClient.OrderStatistics> os = orderClient.statistics(id);
            if (os != null && os.getData() != null) {
                vo.setBoughtCount(os.getData().getBoughtCount());
                vo.setAvgRating(os.getData().getAvgRating());
                vo.setReviewCount(os.getData().getReviewCount());
            }
        } catch (Exception e) {
            log.warn("U13 订单统计降级：userId={}, {}", id, e.getMessage());
        }
        return vo;
    }

    /**
     * U14 封禁：status=BANNED + ban_until + token_version+1 + Redis 封禁标记 + 删 tv 缓存。
     */
    @SneakyThrows
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ban(Long operatorId, Long id, BanDTO dto) {
        User u = requireUser(id);
        checkTarget(operatorId, u);

        LocalDateTime banUntil = Boolean.TRUE.equals(dto.getPermanent())
                ? MallConstants.PERMANENT_UNTIL
                : dto.getBanUntil();
        if (banUntil == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "封禁时间不能为空");
        }
        if (banUntil.isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "封禁时间必须晚于当前时间");
        }

        u.setStatus(MallConstants.STATUS_BANNED);
        u.setBanUntil(banUntil);
        u.setBanReason(dto.getReason());
        u.setTokenVersion((u.getTokenVersion() == null ? 0 : u.getTokenVersion()) + 1);
        userMapper.updateById(u);

        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("banUntil", banUntil);
        marker.put("reason", dto.getReason());
        marker.put("userId", id);
        Duration ttl = MallConstants.PERMANENT_UNTIL.equals(banUntil)
                ? PERMANENT_BAN_TTL : Duration.between(LocalDateTime.now(), banUntil);
        redis.opsForValue().set(MallConstants.CACHE_USER_BANNED + id,
                objectMapper.writeValueAsString(marker), ttl.isNegative() ? Duration.ofMinutes(1) : ttl);
        redis.delete(MallConstants.CACHE_USER_TV + id);

        log.info("封禁用户：operator={}, userId={}, banUntil={}", operatorId, id, banUntil);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("banUntil", banUntil);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Object unban(Long operatorId, Long id) {
        User u = requireUser(id);
        checkTarget(operatorId, u);
        u.setStatus(MallConstants.STATUS_NORMAL);
        u.setBanUntil(null);
        u.setBanReason(null);
        u.setTokenVersion((u.getTokenVersion() == null ? 0 : u.getTokenVersion()) + 1);
        userMapper.updateById(u);
        redis.delete(MallConstants.CACHE_USER_BANNED + id);
        redis.delete(MallConstants.CACHE_USER_TV + id);
        log.info("解封用户：operator={}, userId={}（需重新登录）", operatorId, id);
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishPermission(Long operatorId, Long id, PublishPermissionDTO dto) {
        User u = requireUser(id);
        checkTarget(operatorId, u);

        if (Boolean.TRUE.equals(dto.getCanPublish())) {
            u.setCanPublish(1);
            u.setPublishBanUntil(null);
        } else {
            LocalDateTime until = Boolean.TRUE.equals(dto.getPermanent())
                    ? MallConstants.PERMANENT_UNTIL : dto.getPublishBanUntil();
            if (until == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "限制上架的解除时间不能为空");
            }
            u.setCanPublish(0);
            u.setPublishBanUntil(until);
        }
        userMapper.updateById(u);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publishBanUntil", u.getPublishBanUntil());
        result.put("canPublish", u.getCanPublish() != null && u.getCanPublish() == 1);
        return result;
    }

    /**
     * U17 设置角色：仅 SUPER_ADMIN，只能 USER ↔ ADMIN。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setRole(Long operatorId, Long id, String role) {
        if (!MallConstants.ROLE_USER.equals(role) && !MallConstants.ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.OPERATE_SUPER_ADMIN, "角色只能是 USER 或 ADMIN");
        }
        User u = requireUser(id);
        checkTarget(operatorId, u);
        u.setRole(role);
        u.setTokenVersion((u.getTokenVersion() == null ? 0 : u.getTokenVersion()) + 1);
        userMapper.updateById(u);
        redis.delete(MallConstants.CACHE_USER_TV + id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", role);
        log.info("角色变更：operator={}, userId={}, role={}", operatorId, id, role);
        return result;
    }

    private User requireUser(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return u;
    }

    private void checkTarget(Long operatorId, User target) {
        if (MallConstants.ROLE_SUPER_ADMIN.equals(target.getRole())) {
            throw new BizException(ErrorCode.OPERATE_SUPER_ADMIN);
        }
        if (target.getId().equals(operatorId)) {
            throw new BizException(ErrorCode.OPERATE_SELF);
        }
    }

    private static AdminUserVO toVO(User u) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setEmail(u.getEmail());
        vo.setPhone(MaskUtil.phone(u.getPhone()));
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        vo.setBanUntil(u.getBanUntil());
        vo.setBanReason(u.getBanReason());
        vo.setCanPublish(u.getCanPublish() != null && u.getCanPublish() == 1);
        vo.setPublishBanUntil(u.getPublishBanUntil());
        vo.setCreditScore(u.getCreditScore());
        vo.setLastLoginTime(u.getLastLoginTime());
        vo.setCreateTime(u.getCreateTime());
        return vo;
    }
}
