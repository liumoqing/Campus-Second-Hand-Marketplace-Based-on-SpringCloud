package com.campus.mall.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话（02 §8.1 t_conversation）：一对用户 + 一个商品 = 一条会话（uk_conv），用户 id 恒按小的在前。
 * 这张表没有 deleted，未读数**以 Redis Hash 为准**，DB 的 unread_a/unread_b 只做重建兜底。
 */
@Data
@TableName("t_conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userAId;
    private Long userBId;
    private Long productId;
    private String lastMsg;
    private String lastMsgType;
    private String lastCardType;
    private LocalDateTime lastTime;
    private Integer unreadA;
    private Integer unreadB;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
