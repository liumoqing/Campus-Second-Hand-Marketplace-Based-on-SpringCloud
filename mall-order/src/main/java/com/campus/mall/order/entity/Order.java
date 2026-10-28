package com.campus.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.mall.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单（02 §6.1 t_order）：一件一单，无购物车。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class Order extends BaseEntity {

    private String orderNo;
    private Long buyerId;
    private Long sellerId;
    private Long productId;
    private BigDecimal totalAmount;
    private BigDecimal freight;
    private String freightMode;
    private Integer sameCity;
    private BigDecimal payAmount;
    private String status;
    private String payType;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime receiveTime;
    private LocalDateTime finishTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String logisticsNo;
    private String remark;
    private String addressSnapshot;
    private Integer buyerReviewed;
    private Integer sellerReviewed;
    private Integer version;
}
