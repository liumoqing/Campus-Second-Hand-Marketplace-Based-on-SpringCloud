package com.campus.mall.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单（02 §7.1 t_payment）。一单一支付，uk_order_no 保证幂等。
 *
 * <p>注意：t_payment 没有 deleted 列（02 §1 的列白名单明确写了），所以**不能继承 BaseEntity**，
 * 否则 MyBatis-Plus 会拼出 {@code deleted = 0} 导致 {@code Unknown column 'deleted'}。
 * 时间列交给 DDL 的 DEFAULT CURRENT_TIMESTAMP / ON UPDATE 维护。
 */
@Data
@TableName("t_payment")
public class Payment {

    /** t_payment 是自增主键，与 BaseEntity 的雪花 id 不同 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String payNo;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private String channel;
    private String status;
    private String tradeNo;
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;
    private LocalDateTime payTime;
    private LocalDateTime closeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
