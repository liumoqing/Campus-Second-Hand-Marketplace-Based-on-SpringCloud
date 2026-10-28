package com.campus.mall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.api.R;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.util.MaskUtil;
import com.campus.mall.user.client.OrderClient;
import com.campus.mall.user.client.ProductClient;
import com.campus.mall.user.dto.ProfileUpdateDTO;
import com.campus.mall.user.entity.User;
import com.campus.mall.user.mapper.UserMapper;
import com.campus.mall.user.vo.MyStatisticsVO;
import com.campus.mall.user.vo.SellerCardVO;
import com.campus.mall.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人资料 / 卖家卡片 / 我的统计 / 字典（03 §3 U1~U4、U11）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final OrderClient orderClient;
    private final ProductClient productClient;

    public User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    public UserVO profile(Long userId) {
        return toUserVO(requireUser(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateProfile(Long userId, ProfileUpdateDTO dto) {
        User user = requireUser(userId);
        if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(user.getEmail())) {
            Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .eq(User::getEmail, dto.getEmail()).ne(User::getId, userId));
            if (exists != null && exists > 0) {
                throw new BizException(ErrorCode.EMAIL_EXISTS);
            }
            user.setEmail(dto.getEmail());
        }
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }
        if (dto.getSignature() != null) {
            user.setSignature(dto.getSignature());
        }
        if (dto.getProvince() != null) {
            user.setProvince(dto.getProvince());
        }
        if (dto.getCity() != null) {
            user.setCity(dto.getCity());
        }
        if (dto.getDistrict() != null) {
            user.setDistrict(dto.getDistrict());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        userMapper.updateById(user);
        return true;
    }

    /**
     * U3 卖家主页：本库字段 + mall-order 统计 + mall-product 统计，两次 Feign 失败都降级为 0。
     */
    public SellerCardVO sellerCard(Long sellerId) {
        User user = requireUser(sellerId);
        SellerCardVO vo = new SellerCardVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setCity(user.getCity());
        vo.setSignature(user.getSignature());
        vo.setCreditScore(user.getCreditScore());
        vo.setJoinedAt(user.getCreateTime());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setRegisterDays(registerDays(user.getCreateTime()));

        OrderClient.OrderStatistics os = safeOrderStatistics(sellerId);
        vo.setSoldCount(os.getSoldCount());
        vo.setAvgRating(os.getAvgRating());
        vo.setReviewCount(os.getReviewCount());

        ProductClient.ProductStatistics ps = safeProductStatistics(sellerId);
        vo.setOnSaleCount(ps.getOnSaleCount());
        return vo;
    }

    public MyStatisticsVO statistics(Long userId) {
        MyStatisticsVO vo = new MyStatisticsVO();
        ProductClient.ProductStatistics ps = safeProductStatistics(userId);
        vo.setOnSaleCount(ps.getOnSaleCount());
        vo.setDraftCount(ps.getDraftCount());
        OrderClient.OrderStatistics os = safeOrderStatistics(userId);
        vo.setSoldCount(os.getSoldCount());
        vo.setBoughtCount(os.getBoughtCount());
        vo.setAvgRating(os.getAvgRating());
        vo.setPendingShipCount(os.getPendingShipCount());
        vo.setPendingReceiveCount(os.getPendingReceiveCount());
        // 收藏数由 mall-product 的 P34 一并返回（t_favorite 在商品库）
        vo.setFavoriteCount(ps.getFavoriteCount());
        return vo;
    }

    public Map<String, List<Map<String, Object>>> dict() {
        Map<String, List<Map<String, Object>>> map = new LinkedHashMap<>();
        map.put("gender", List.of(
                item(0, "未知"), item(1, "男"), item(2, "女")));
        map.put("role", List.of(
                item(com.campus.mall.common.constant.MallConstants.ROLE_USER, "普通用户"), item(com.campus.mall.common.constant.MallConstants.ROLE_ADMIN, "普通管理员"),
                item(com.campus.mall.common.constant.MallConstants.ROLE_SUPER_ADMIN, "超级管理员")));
        map.put("status", List.of(
                item(com.campus.mall.common.constant.MallConstants.STATUS_NORMAL, "正常"), item(com.campus.mall.common.constant.MallConstants.STATUS_BANNED, "已封禁")));
        return map;
    }

    private static Map<String, Object> item(Object value, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        return m;
    }

    private static Long registerDays(LocalDateTime createTime) {
        if (createTime == null) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(createTime.toLocalDate(), LocalDate.now());
    }

    private OrderClient.OrderStatistics safeOrderStatistics(Long userId) {
        try {
            R<OrderClient.OrderStatistics> r = orderClient.statistics(userId);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("调用 mall-order 统计失败，降级为 0：userId={}, {}", userId, e.getMessage());
        }
        return new OrderClient.OrderStatistics();
    }

    private ProductClient.ProductStatistics safeProductStatistics(Long sellerId) {
        try {
            R<ProductClient.ProductStatistics> r = productClient.statistics(sellerId);
            if (r != null && r.getData() != null) {
                return r.getData();
            }
        } catch (Exception e) {
            log.warn("调用 mall-product 统计失败，降级为 0：sellerId={}, {}", sellerId, e.getMessage());
        }
        return new ProductClient.ProductStatistics();
    }

    public static UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(MaskUtil.phone(user.getPhone()));
        vo.setEmail(user.getEmail());
        vo.setGender(user.getGender());
        vo.setProvince(user.getProvince());
        vo.setCity(user.getCity());
        vo.setDistrict(user.getDistrict());
        vo.setSignature(user.getSignature());
        vo.setCreditScore(user.getCreditScore());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCanPublish(user.getCanPublish() != null && user.getCanPublish() == 1);
        vo.setPublishBanUntil(user.getPublishBanUntil());
        vo.setBanUntil(user.getBanUntil());
        vo.setBanReason(user.getBanReason());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
