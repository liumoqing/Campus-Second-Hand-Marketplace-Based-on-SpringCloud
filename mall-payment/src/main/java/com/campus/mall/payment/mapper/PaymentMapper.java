package com.campus.mall.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 支付单 Mapper。退款用条件更新实现幂等（影响 0 行=已退过）。 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Update("UPDATE t_payment SET status='REFUNDED', refund_amount=#{amount}, refund_time=NOW(), update_time=NOW() "
            + "WHERE order_no=#{orderNo} AND status='SUCCESS'")
    int markRefunded(@Param("orderNo") String orderNo, @Param("amount") java.math.BigDecimal amount);

    @Update("UPDATE t_payment SET status='CLOSED', close_time=NOW(), update_time=NOW() "
            + "WHERE status='INIT' AND create_time < DATE_SUB(NOW(), INTERVAL #{minutes} MINUTE)")
    int closeStale(@Param("minutes") int minutes);
}
