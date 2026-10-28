package com.campus.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单明细（02 §6.2 t_order_item）：标题/封面/价格都是下单时快照。 */
@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private String productTitle;
    private String productCover;
    private BigDecimal price;
    private Integer quantity;
    private LocalDateTime createTime;
}
