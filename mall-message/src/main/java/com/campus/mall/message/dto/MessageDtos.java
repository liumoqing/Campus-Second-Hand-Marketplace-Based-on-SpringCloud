package com.campus.mall.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 消息服务的请求体集合（M4/M5/M12/M13 + 内部通知）。 */
public class MessageDtos {

    /** M4 发消息（HTTP 兜底，WS 断了才用） */
    @Data
    public static class Send {
        @NotNull(message = "会话不能为空")
        private Long conversationId;
        @NotNull(message = "收件人不能为空")
        private Long toId;
        /** TEXT / IMAGE，客户端不能发 PRODUCT / ORDER（60004） */
        @NotBlank(message = "消息类型不能为空")
        private String contentType;
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息太长")
        private String content;
        private String clientMsgId;
    }

    /** M5 已读：两种语义互斥，都传或都不传都是 10001 */
    @Data
    public static class Read {
        private Long conversationId;
        private Long lastMsgId;
        private List<Long> noticeIds;
    }

    /** M12 建/取会话 */
    @Data
    public static class CreateConversation {
        @NotNull(message = "对方用户不能为空")
        private Long peerId;
        @NotNull(message = "商品不能为空")
        private Long productId;
        /** 默认 true：首次建会话时发一条 PRODUCT 卡片开场 */
        private Boolean openWithProductCard;
    }

    /** M13 订单卡片（只有 mall-order 调） */
    @Data
    public static class Card {
        @NotBlank
        private String orderNo;
        @NotBlank
        private String cardType;
        @NotNull
        private Long buyerId;
        @NotNull
        private Long sellerId;
        @NotNull
        private Long productId;
        private String productTitle;
        private String productCover;
        private String statusText;
        private String actionText;
    }

    /** M11 站内通知（product/order/payment 调） */
    @Data
    public static class Notice {
        @NotNull
        private Long userId;
        /** SYSTEM / ORDER / REFUND */
        private String type;
        @NotBlank
        private String title;
        @NotBlank
        private String content;
        private String bizId;
        private String bizType;
    }

    /** 强制下线（mall-user 封禁时调） */
    @Data
    public static class Kick {
        @NotNull
        private Long userId;
        private String reason;
        private String banUntil;
        private String message;
    }
}
