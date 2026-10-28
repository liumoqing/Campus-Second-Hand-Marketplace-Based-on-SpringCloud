package com.campus.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.order.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    @Update("UPDATE t_refund SET status=#{to}, handle_time=#{now}, handler_id=#{handlerId}, handle_remark=#{remark}, "
            + "update_time=#{now} WHERE id=#{id} AND status='APPLYING'")
    int transit(@Param("id") Long id, @Param("to") String to, @Param("now") LocalDateTime now,
                @Param("handlerId") Long handlerId, @Param("remark") String remark);
}
