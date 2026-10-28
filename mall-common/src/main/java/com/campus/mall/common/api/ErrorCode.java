package com.campus.mall.common.api;

import lombok.Getter;

/**
 * 全项目错误码（与 01 §6.2 一一对应，新增前先改文档）。
 * 段位：1xxxx 通用 / 2xxxx 账号 / 3xxxx 商品 / 4xxxx 订单 / 5xxxx 支付 / 6xxxx 消息
 */
@Getter
public enum ErrorCode {

    // ---------- 1xxxx 通用 ----------
    PARAM_INVALID(10001, "参数校验失败"),
    UNAUTHORIZED(10002, "未登录或登录已过期"),
    FORBIDDEN(10003, "无权限执行该操作"),
    NOT_FOUND(10004, "资源不存在或不属于你"),
    STATUS_NOT_ALLOWED(10005, "业务状态不允许当前操作"),
    DUPLICATE_SUBMIT(10006, "重复提交，请勿重复操作"),
    SERVICE_DEGRADED(10007, "服务繁忙，请稍后再试"),
    UPLOAD_FAILED(10008, "文件上传失败：类型或大小不符合要求"),
    INNER_TOKEN_INVALID(10009, "内部调用凭据无效"),

    // ---------- 2xxxx 账号 ----------
    USERNAME_EXISTS(20001, "用户名已存在"),
    LOGIN_FAILED(20002, "用户名/邮箱或密码错误"),
    ACCOUNT_BANNED(20003, "账号已被封禁"),
    CAPTCHA_INVALID(20004, "验证码错误或已过期"),
    PASSWORD_ERROR(20005, "密码错误或两次输入不一致"),
    EMAIL_EXISTS(20006, "邮箱已被注册"),
    AVATAR_INVALID(20007, "头像地址不合法"),
    OPERATE_SUPER_ADMIN(20008, "不能操作超级管理员账户"),
    OPERATE_SELF(20009, "不能对自己执行该操作"),
    ADDRESS_LIMIT(20010, "收货地址数量已达上限（最多 10 条）"),

    // ---------- 3xxxx 商品 ----------
    PRODUCT_NOT_FOUND(30001, "商品不存在"),
    PRODUCT_UNAVAILABLE(30002, "商品已下架、已售出或不可购买"),
    STOCK_LOCKED(30003, "手慢了，该商品已被他人锁定"),
    DRAFT_INCOMPLETE(30004, "草稿信息不完整，无法发布"),
    PRODUCT_NOT_OWNER(30005, "不是本人发布的商品"),
    CATEGORY_NOT_FOUND(30006, "分类不存在"),
    CATEGORY_HAS_PRODUCT(30007, "该分类下仍有商品，不能删除"),
    CATEGORY_LEVEL_EXCEEDED(30008, "分类层级超限，最多两级"),
    PUBLISH_FORBIDDEN(30009, "上架权限已被限制"),

    // ---------- 4xxxx 订单 ----------
    ORDER_NOT_FOUND(40001, "订单不存在"),
    ORDER_STATUS_NOT_ALLOWED(40002, "订单状态不允许该操作"),
    ORDER_SELF_PRODUCT(40003, "不能购买自己发布的商品"),
    ORDER_CROSS_CITY_PICKUP(40004, "仅自提商品不支持跨城收货"),
    REVIEW_DUPLICATED(40005, "该方向已评价过"),
    ORDER_TIMEOUT_CLOSED(40006, "订单已超时关闭"),
    REFUND_DUPLICATED(40007, "该订单已申请过退款或售后"),
    REFUND_EXPIRED(40008, "已超出售后期限（完成后 7 天内可申请）"),

    // ---------- 5xxxx 支付 ----------
    PAY_CREATE_FAILED(50001, "支付单创建失败或支付渠道不可用"),
    PAY_STATUS_NOT_ALLOWED(50002, "订单当前不是待付款状态"),
    PAY_NOT_FOUND(50003, "支付单不存在"),
    REFUND_AMOUNT_EXCEEDED(50004, "退款金额超出可退额度"),

    // ---------- 6xxxx 消息 ----------
    CONVERSATION_NOT_FOUND(60001, "会话不存在"),
    MESSAGE_SELF(60002, "不能给自己发消息"),
    MESSAGE_PEER_BANNED(60003, "该用户已被封禁，暂时无法收发消息"),
    MESSAGE_TYPE_UNSUPPORTED(60004, "消息类型不支持"),
    MESSAGE_CONTENT_INVALID(60005, "消息内容不合法"),
    MESSAGE_TOO_FREQUENT(60006, "发送过于频繁，请稍后再试"),
    MESSAGE_SEND_FAILED(60007, "消息发送失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
