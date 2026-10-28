package com.campus.mall.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息（02 §8.2 t_message）。
 *
 * <p>TEXT/IMAGE 的 content 是纯文本或图片 URL；PRODUCT/ORDER 的 content 是卡片 JSON。
 * 卡片消息的 {@code clientMsgId} 固定写 {@code CARD:{orderNo}:{cardType}}，
 * 靠 {@code uk_client_msg(from_id, client_msg_id)} 天然幂等（MQ 重投、兜底再调都只会有一条）。
 */
@Data
@TableName("t_message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long fromId;
    private Long toId;
    /** TEXT / IMAGE / PRODUCT / ORDER */
    private String contentType;
    private String cardType;
    private String content;
    /** SENT / DELIVERED / READ */
    private String status;
    private String clientMsgId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
