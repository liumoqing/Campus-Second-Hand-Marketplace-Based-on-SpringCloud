package com.campus.mall.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.mall.common.api.ErrorCode;
import com.campus.mall.common.exception.BizException;
import com.campus.mall.common.util.MaskUtil;
import com.campus.mall.user.dto.AddressDTO;
import com.campus.mall.user.entity.UserAddress;
import com.campus.mall.user.mapper.UserAddressMapper;
import com.campus.mall.user.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 收货地址（03 §3 U5~U10）：单用户最多 10 条，默认地址唯一由业务保证。
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private static final int MAX_ADDRESS = 10;

    private final UserAddressMapper addressMapper;

    public List<AddressVO> list(Long userId) {
        List<UserAddress> rows = addressMapper.selectList(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId));
        return rows.stream()
                .sorted(Comparator.comparing((UserAddress a) -> a.getIsDefault() != null && a.getIsDefault() == 1 ? 0 : 1)
                        .thenComparing(UserAddress::getId, Comparator.reverseOrder()))
                .map(AddressService::toVO)
                .toList();
    }

    public AddressVO getDefault(Long userId) {
        UserAddress a = addressMapper.selectOne(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId).eq(UserAddress::getIsDefault, 1).last("LIMIT 1"));
        return a == null ? null : toVO(a);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, AddressDTO dto) {
        Long count = addressMapper.selectCount(Wrappers.<UserAddress>lambdaQuery().eq(UserAddress::getUserId, userId));
        if (count != null && count >= MAX_ADDRESS) {
            throw new BizException(ErrorCode.ADDRESS_LIMIT);
        }
        boolean first = count == null || count == 0;
        boolean asDefault = first || Boolean.TRUE.equals(dto.getIsDefault());
        if (asDefault) {
            clearDefault(userId);
        }
        UserAddress a = new UserAddress();
        a.setUserId(userId);
        copy(dto, a);
        a.setIsDefault(asDefault ? 1 : 0);
        addressMapper.insert(a);
        return a.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean update(Long userId, Long id, AddressDTO dto) {
        UserAddress a = requireOwn(userId, id);
        copy(dto, a);
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearDefault(userId);
            a.setIsDefault(1);
        }
        addressMapper.updateById(a);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long userId, Long id) {
        UserAddress a = requireOwn(userId, id);
        addressMapper.deleteById(id);
        if (a.getIsDefault() != null && a.getIsDefault() == 1) {
            UserAddress newest = addressMapper.selectOne(Wrappers.<UserAddress>lambdaQuery()
                    .eq(UserAddress::getUserId, userId).orderByDesc(UserAddress::getId).last("LIMIT 1"));
            if (newest != null) {
                newest.setIsDefault(1);
                addressMapper.updateById(newest);
            }
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean setDefault(Long userId, Long id) {
        UserAddress a = requireOwn(userId, id);
        clearDefault(userId);
        a.setIsDefault(1);
        addressMapper.updateById(a);
        return true;
    }

    /**
     * 下单时校验地址归属（mall-order 通过 /inner 用）。
     */
    public UserAddress requireOwn(Long userId, Long id) {
        UserAddress a = addressMapper.selectById(id);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return a;
    }

    private void clearDefault(Long userId) {
        addressMapper.update(null, Wrappers.<UserAddress>lambdaUpdate()
                .set(UserAddress::getIsDefault, 0)
                .eq(UserAddress::getUserId, userId).eq(UserAddress::getIsDefault, 1));
    }

    private static void copy(AddressDTO dto, UserAddress a) {
        a.setReceiver(dto.getReceiver());
        a.setPhone(dto.getPhone());
        a.setProvince(dto.getProvince());
        a.setCity(dto.getCity());
        a.setDistrict(dto.getDistrict());
        a.setDetail(dto.getDetail());
    }

    public static AddressVO toVO(UserAddress a) {
        AddressVO vo = new AddressVO();
        vo.setId(a.getId());
        vo.setReceiver(a.getReceiver());
        vo.setPhone(MaskUtil.phone(a.getPhone()));
        vo.setProvince(a.getProvince());
        vo.setCity(a.getCity());
        vo.setDistrict(a.getDistrict());
        vo.setDetail(a.getDetail());
        vo.setIsDefault(a.getIsDefault() != null && a.getIsDefault() == 1);
        return vo;
    }
}
