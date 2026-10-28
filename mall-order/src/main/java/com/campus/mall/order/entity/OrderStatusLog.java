package com.campus.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 订单状态流水（02 §6.3 t_order_status_log）：每次状态迁移都留痕，详情页时间线用它。 */
@Data
@TableName("t_order_status_log")
public class OrderStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    /** BUYER / SELLER / ADMIN / SYSTEM */
    private String operatorType;
    private String remark;
    private LocalDateTime createTime;
}
