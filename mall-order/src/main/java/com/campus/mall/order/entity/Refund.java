package com.campus.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退款/售后单（02 §6.5 t_refund）：uk_order 保证一单一次，before_status 决定拒绝后回退到哪。 */
@Data
@TableName("t_refund")
public class Refund {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundNo;
    private Long orderId;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal amount;
    private String reason;
    /** APPLYING / AGREED / REJECTED / REFUNDED / CLOSED */
    private String status;
    private String beforeStatus;
    private LocalDateTime applyTime;
    private LocalDateTime handleTime;
    private Long handlerId;
    private String handleRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
