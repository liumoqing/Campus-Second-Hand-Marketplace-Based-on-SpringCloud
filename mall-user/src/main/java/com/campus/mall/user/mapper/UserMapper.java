package com.campus.mall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 信用分扣减（01 BR-23：评价 ≤2 星扣 2 分，退款扣 1 分），带下限 0 与软删过滤。
     * 用条件 UPDATE 而不是「读-改-写」，避免并发下丢更新。
     */
    @Update("UPDATE t_user SET credit_score = GREATEST(credit_score - #{score}, 0), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int deductCredit(@Param("id") Long id, @Param("score") int score);
}
