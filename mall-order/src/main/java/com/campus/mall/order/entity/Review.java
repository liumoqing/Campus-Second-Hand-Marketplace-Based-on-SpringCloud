package com.campus.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 双向评价（02 §6.4 t_review）：uk_order_direction 保证一个方向上只有一条。 */
@Data
@TableName("t_review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    /** BUYER_TO_SELLER / SELLER_TO_BUYER */
    private String direction;
    private Long reviewerId;
    private Long revieweeId;
    private Integer rating;
    private String content;
    private String images;
    private Integer anonymous;
    private String reply;
    private LocalDateTime replyTime;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
