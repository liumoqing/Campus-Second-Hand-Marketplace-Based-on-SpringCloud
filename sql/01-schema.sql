-- ============================================================
-- 晴川二手市场 · 01-schema.sql
-- 建 5 个业务库 + 应用账号授权 + 17 张业务表 + undo_log（mall_product / mall_order）
-- 生成时间：2026-10-27 20:31:30（内容来自 文档归类\02-数据库设计.md）
-- 用法：mysql --host=127.0.0.1 --port=3306 --user=root --password=123456 < sql/01-schema.sql
-- 注意：mall_seata 用 Seata 官方脚本导入，不在此文件
-- ============================================================

-- sql/01-schema.sql
CREATE DATABASE IF NOT EXISTS `mall_user`    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `mall_product` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `mall_order`   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `mall_payment` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `mall_message` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `mall_seata`   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 应用账号（最小权限，仅业务库 DML）
CREATE USER IF NOT EXISTS 'mall_app'@'localhost' IDENTIFIED BY 'Mall@2026';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mall_user`.*    TO 'mall_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mall_product`.* TO 'mall_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mall_order`.*   TO 'mall_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mall_payment`.* TO 'mall_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mall_message`.* TO 'mall_app'@'localhost';
-- Seata Server 在 store.mode=db 时用该账号读写全局事务表（见 01 文档附录 D.2）
GRANT SELECT, INSERT, UPDATE, DELETE ON `mall_seata`.*   TO 'mall_app'@'localhost';
FLUSH PRIVILEGES;

-- ------------------------------------------------------------
-- mall_user
-- ------------------------------------------------------------
USE `mall_user`;

CREATE TABLE `t_user` (
  `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`          varchar(32)  NOT NULL COMMENT '登录名',
  `password`          varchar(100) NOT NULL COMMENT 'BCrypt 密文',
  `nickname`          varchar(32)  NOT NULL COMMENT '昵称',
  `avatar`            varchar(255) NOT NULL DEFAULT '' COMMENT '头像URL（注册必填）',
  `phone`             varchar(20)           DEFAULT NULL COMMENT '手机号',
  `email`             varchar(64)  NOT NULL COMMENT '邮箱（登录账号之一）',
  `gender`            tinyint      NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
  `province`          varchar(32)           DEFAULT NULL COMMENT '常驻省',
  `city`              varchar(32)           DEFAULT NULL COMMENT '常驻市',
  `district`          varchar(32)           DEFAULT NULL COMMENT '常驻区县',
  `signature`         varchar(200)          DEFAULT NULL COMMENT '个性签名',
  `credit_score`      int          NOT NULL DEFAULT 100 COMMENT '信用分',
  `role`              varchar(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN/SUPER_ADMIN',
  `status`            varchar(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/BANNED',
  `ban_until`         datetime              DEFAULT NULL COMMENT '封禁解封时间，9999-12-31 23:59:59=永久',
  `ban_reason`        varchar(200)          DEFAULT NULL COMMENT '封禁原因',
  `can_publish`       tinyint      NOT NULL DEFAULT 1 COMMENT '1可上架 0被限制上架',
  `publish_ban_until` datetime              DEFAULT NULL COMMENT '上架权限解禁时间，9999=永久',
  `token_version`     int          NOT NULL DEFAULT 0 COMMENT '令牌版本，封禁/改角色时+1',
  `last_login_time`   datetime              DEFAULT NULL COMMENT '最后登录时间',
  `create_time`       datetime     NOT NULL COMMENT '创建时间',
  `update_time`       datetime     NOT NULL COMMENT '更新时间',
  `deleted`           tinyint      NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_status` (`status`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `t_user_address` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`     bigint unsigned NOT NULL COMMENT '用户ID',
  `receiver`    varchar(32)  NOT NULL COMMENT '收货人',
  `phone`       varchar(20)  NOT NULL COMMENT '联系电话',
  `province`    varchar(32)  NOT NULL COMMENT '省',
  `city`        varchar(32)  NOT NULL COMMENT '市',
  `district`    varchar(32)  NOT NULL COMMENT '区县',
  `detail`      varchar(255) NOT NULL COMMENT '详细地址',
  `is_default`  tinyint      NOT NULL DEFAULT 0 COMMENT '是否默认',
  `create_time` datetime     NOT NULL,
  `update_time` datetime     NOT NULL,
  `deleted`     tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';


-- ------------------------------------------------------------
-- mall_product
-- ------------------------------------------------------------
USE `mall_product`;

CREATE TABLE `t_category` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `parent_id`   bigint unsigned NOT NULL DEFAULT 0 COMMENT '父分类，0为顶级',
  `name`        varchar(32)  NOT NULL COMMENT '分类名',
  `icon`        varchar(255)          DEFAULT NULL COMMENT '图标',
  `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序值，越小越前',
  `status`      tinyint      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `create_time` datetime     NOT NULL,
  `update_time` datetime     NOT NULL,
  `deleted`     tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_name` (`parent_id`, `name`, `deleted`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

CREATE TABLE `t_product` (
  `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
  `seller_id`       bigint unsigned NOT NULL COMMENT '卖家ID',
  `category_id`     bigint unsigned NOT NULL COMMENT '分类ID',
  `title`           varchar(100) NOT NULL COMMENT '标题',
  `description`     text COMMENT '描述',
  `price`           decimal(10,2) NOT NULL COMMENT '售价',
  `original_price`  decimal(10,2) DEFAULT NULL COMMENT '原价',
  `condition_level` tinyint      NOT NULL DEFAULT 9 COMMENT '成色1-10',
  `cover_url`       varchar(255) NOT NULL COMMENT '封面图',
  `province`        varchar(32)  NOT NULL COMMENT '发货地-省',
  `city`            varchar(32)  NOT NULL COMMENT '发货地-市',
  `district`        varchar(32)  NOT NULL COMMENT '发货地-区县',
  `freight_mode`    varchar(16)  NOT NULL DEFAULT 'DISTANCE' COMMENT 'FREE_SHIPPING/DISTANCE/SELF_PICKUP',
  `status`          varchar(20)  NOT NULL DEFAULT 'ON_SALE' COMMENT 'DRAFT/ON_SALE/LOCKED/SOLD/OFF_SHELF',
  `lock_order_no`   varchar(32)  DEFAULT NULL COMMENT '锁定该商品的订单号（LOCKED 时写入，用于扣库存幂等与超时回补）',
  `off_shelf_reason` varchar(200) DEFAULT NULL COMMENT '下架原因（本人下架或管理员强制下架，用于审计与通知卖家）',
  `view_count`      int          NOT NULL DEFAULT 0 COMMENT '浏览量',
  `favorite_count`  int          NOT NULL DEFAULT 0 COMMENT '收藏数（P15/P16 同事务 ±1）',
  `comment_count`   int          NOT NULL DEFAULT 0 COMMENT '评论数',
  `stock`           int          NOT NULL DEFAULT 1 COMMENT '库存（一件一单，恒为1；扣减即置0，回补置1）',
  `sold_time`       datetime     DEFAULT NULL COMMENT '售出时间（置 SOLD 时写）',
  `version`         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_time`     datetime     NOT NULL,
  `update_time`     datetime     NOT NULL,
  `deleted`         tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cat_status` (`category_id`, `status`, `create_time`),
  KEY `idx_seller` (`seller_id`, `status`),
  KEY `idx_status_create` (`status`, `create_time`),
  KEY `idx_status_city_time` (`status`, `city`, `create_time`),
  KEY `idx_price` (`price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

CREATE TABLE `t_product_image` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `product_id`  bigint unsigned NOT NULL,
  `url`         varchar(255) NOT NULL COMMENT '图片地址',
  `sort`        int          NOT NULL DEFAULT 0,
  `create_time` datetime     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片表';

CREATE TABLE `t_product_comment` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `product_id`  bigint unsigned NOT NULL COMMENT '商品ID',
  `user_id`     bigint unsigned NOT NULL COMMENT '评论人',
  `parent_id`   bigint unsigned NOT NULL DEFAULT 0 COMMENT '父评论ID，0为一级',
  `content`     varchar(500) NOT NULL COMMENT '评论内容',
  `images`      json          DEFAULT NULL COMMENT '图片URL数组',
  `status`      tinyint       NOT NULL DEFAULT 1 COMMENT '1正常 0被管理员隐藏（不删除，仅前台不展示）',
  `create_time` datetime      NOT NULL,
  `update_time` datetime      NOT NULL COMMENT '至少管理员隐藏时更新',
  `deleted`     tinyint       NOT NULL DEFAULT 0 COMMENT '1=已删除（本人或管理员物理删除的软标记），查询必须同时带 status=1 AND deleted=0',
  PRIMARY KEY (`id`),
  KEY `idx_product_time` (`product_id`, `parent_id`, `create_time`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评论表';

CREATE TABLE `t_favorite` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`     bigint unsigned NOT NULL,
  `product_id`  bigint unsigned NOT NULL,
  `create_time` datetime     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';


-- ------------------------------------------------------------
-- mall_order
-- ------------------------------------------------------------
USE `mall_order`;

CREATE TABLE `t_order` (
  `id`               bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_no`         varchar(32)  NOT NULL COMMENT '业务单号',
  `buyer_id`         bigint unsigned NOT NULL COMMENT '买家',
  `seller_id`        bigint unsigned NOT NULL COMMENT '卖家',
  `product_id`       bigint unsigned NOT NULL COMMENT '商品ID',
  `total_amount`     decimal(10,2) NOT NULL COMMENT '商品总额',
  `freight`          decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
  `freight_mode`     varchar(16)  NOT NULL DEFAULT 'DISTANCE' COMMENT '运费方式快照 FREE_SHIPPING/DISTANCE/SELF_PICKUP',
  `same_city`        tinyint      NOT NULL DEFAULT 0 COMMENT '同城快照 1是 0否',
  `pay_amount`       decimal(10,2) NOT NULL COMMENT '应付金额',
  `status`           varchar(20)  NOT NULL DEFAULT 'UNPAID' COMMENT '订单状态',
  `pay_type`         varchar(16)  DEFAULT NULL COMMENT 'MOCK 模拟支付',
  `pay_time`         datetime     DEFAULT NULL,
  `ship_time`        datetime     DEFAULT NULL,
  `receive_time`     datetime     DEFAULT NULL,
  `finish_time`      datetime     DEFAULT NULL,
  `cancel_time`      datetime     DEFAULT NULL,
  `cancel_reason`    varchar(255) DEFAULT NULL,
  `logistics_no`     varchar(64)  DEFAULT NULL,
  `remark`           varchar(255) DEFAULT NULL COMMENT '买家留言',
  `address_snapshot` json         DEFAULT NULL COMMENT '地址快照',
  `buyer_reviewed`   tinyint      NOT NULL DEFAULT 0 COMMENT '买家已评价',
  `seller_reviewed`  tinyint      NOT NULL DEFAULT 0 COMMENT '卖家已评价',
  `version`          int          NOT NULL DEFAULT 0,
  `create_time`      datetime     NOT NULL,
  `update_time`      datetime     NOT NULL,
  `deleted`          tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_buyer_status` (`buyer_id`, `status`, `create_time`),
  KEY `idx_seller_status` (`seller_id`, `status`, `create_time`),
  KEY `idx_status_create` (`status`, `create_time`),
  KEY `idx_product` (`product_id`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE `t_order_item` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id`      bigint unsigned NOT NULL,
  `product_id`    bigint unsigned NOT NULL,
  `product_title` varchar(100) NOT NULL COMMENT '下单时标题快照',
  `product_cover` varchar(255) NOT NULL COMMENT '下单时封面快照',
  `price`         decimal(10,2) NOT NULL COMMENT '下单时单价',
  `quantity`      int          NOT NULL DEFAULT 1,
  `create_time`   datetime     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

CREATE TABLE `t_order_status_log` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id`      bigint unsigned NOT NULL,
  `from_status`   varchar(20)  DEFAULT NULL,
  `to_status`     varchar(20)  NOT NULL,
  `operator_id`   bigint unsigned DEFAULT NULL,
  `operator_type` varchar(16)  NOT NULL COMMENT 'BUYER/SELLER/ADMIN/SYSTEM',
  `remark`        varchar(255) DEFAULT NULL,
  `create_time`   datetime     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流水表';

CREATE TABLE `t_review` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id`     bigint unsigned NOT NULL,
  `product_id`   bigint unsigned NOT NULL,
  `direction`    varchar(20)  NOT NULL COMMENT 'BUYER_TO_SELLER/SELLER_TO_BUYER',
  `reviewer_id`  bigint unsigned NOT NULL COMMENT '评价人',
  `reviewee_id`  bigint unsigned NOT NULL COMMENT '被评价人',
  `rating`       tinyint      NOT NULL COMMENT '1-5星',
  `content`      varchar(500) DEFAULT NULL,
  `images`       json         DEFAULT NULL COMMENT '图片URL数组',
  `anonymous`    tinyint      NOT NULL DEFAULT 0,
  `reply`        varchar(500) DEFAULT NULL COMMENT '被评价方回复',
  `reply_time`   datetime     DEFAULT NULL,
  `create_time`  datetime     NOT NULL,
  `deleted`      tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_direction` (`order_id`, `direction`),
  KEY `idx_reviewee` (`reviewee_id`, `direction`),
  KEY `idx_reviewer` (`reviewer_id`),
  KEY `idx_product_time` (`product_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='双向评价表';

CREATE TABLE `t_refund` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `refund_no`     varchar(32)  NOT NULL COMMENT '退款单号',
  `order_id`      bigint unsigned NOT NULL,
  `buyer_id`      bigint unsigned NOT NULL,
  `seller_id`     bigint unsigned NOT NULL,
  `amount`        decimal(10,2) NOT NULL,
  `reason`        varchar(255) NOT NULL,
  `status`        varchar(16)  NOT NULL DEFAULT 'APPLYING' COMMENT 'APPLYING/AGREED/REJECTED/REFUNDED/CLOSED',
  `before_status` varchar(20)  NOT NULL COMMENT '申请前订单状态（UNSHIPPED/UNRECEIVED/COMPLETED），拒绝时按它回退',
  `apply_time`    datetime     NOT NULL,
  `handle_time`   datetime     DEFAULT NULL,
  `handler_id`    bigint unsigned DEFAULT NULL,
  `handle_remark` varchar(255) DEFAULT NULL,
  `create_time`   datetime     NOT NULL,
  `update_time`   datetime     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_buyer_time` (`buyer_id`, `create_time`),
  KEY `idx_seller_time` (`seller_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

CREATE TABLE `t_mq_local_msg` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `msg_id`      varchar(64)  NOT NULL COMMENT '消息唯一ID',
  `topic`       varchar(64)  NOT NULL,
  `tag`         varchar(64)  DEFAULT NULL,
  `payload`     text         NOT NULL,
  `status`      varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  `retry_count` int          NOT NULL DEFAULT 0,
  `next_time`   datetime     DEFAULT NULL COMMENT '下次重试时间',
  `create_time` datetime     NOT NULL,
  `update_time` datetime     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_id` (`msg_id`),
  KEY `idx_status_next` (`status`, `next_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（预留）';


-- ------------------------------------------------------------
-- mall_payment
-- ------------------------------------------------------------
USE `mall_payment`;

CREATE TABLE `t_payment` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `pay_no`        varchar(32)  NOT NULL COMMENT '支付单号',
  `order_no`      varchar(32)  NOT NULL COMMENT '订单号',
  `user_id`       bigint unsigned NOT NULL COMMENT '付款人',
  `amount`        decimal(10,2) NOT NULL COMMENT '支付金额',
  `channel`       varchar(16)  NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK/ALIPAY/WECHAT',
  `status`        varchar(16)  NOT NULL DEFAULT 'INIT' COMMENT 'INIT/SUCCESS/FAILED/CLOSED/REFUNDED',
  `trade_no`      varchar(64)  DEFAULT NULL COMMENT '第三方交易号',
  `refund_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '已退金额',
  `refund_time`   datetime     DEFAULT NULL COMMENT '退款时间',
  `pay_time`      datetime     DEFAULT NULL,
  `close_time`    datetime     DEFAULT NULL,
  `create_time`   datetime     NOT NULL,
  `update_time`   datetime     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';


-- ------------------------------------------------------------
-- mall_message
-- ------------------------------------------------------------
USE `mall_message`;

CREATE TABLE `t_conversation` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_a_id`   bigint unsigned NOT NULL COMMENT '较小的userId',
  `user_b_id`   bigint unsigned NOT NULL COMMENT '较大的userId',
  `product_id`  bigint unsigned NOT NULL COMMENT '围绕的商品',
  `last_msg`    varchar(255) DEFAULT NULL COMMENT '最后一条消息摘要（纯文本，列表直接展示）',
  `last_msg_type`   varchar(16) DEFAULT NULL COMMENT '最后一条消息的类型 TEXT/IMAGE/PRODUCT/ORDER（M2 摘要规则用）',
  `last_card_type`  varchar(32) DEFAULT NULL COMMENT '最后一条如果是订单卡片，它的 card_type（前端决定摘要文案与图标）',
  `last_time`   datetime     NOT NULL COMMENT '最后一条消息时间（排序用；无消息时等于 create_time）',
  `unread_a`    int          NOT NULL DEFAULT 0 COMMENT 'a 的未读数',
  `unread_b`    int          NOT NULL DEFAULT 0 COMMENT 'b 的未读数',
  `create_time` datetime     NOT NULL,
  `update_time` datetime     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv` (`user_a_id`, `user_b_id`, `product_id`),
  KEY `idx_a_time` (`user_a_id`, `last_time`),
  KEY `idx_b_time` (`user_b_id`, `last_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

CREATE TABLE `t_message` (
  `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint unsigned NOT NULL,
  `from_id`         bigint unsigned NOT NULL,
  `to_id`           bigint unsigned NOT NULL,
  `content_type`    varchar(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/PRODUCT/ORDER',
  `card_type`       varchar(32)  DEFAULT NULL COMMENT '卡片子类型：ORDER_CREATED/ORDER_PAID/ORDER_SHIPPED/ORDER_RECEIVED/ORDER_CANCELLED/REFUND_APPLIED/REFUND_DONE（仅 content_type=ORDER 时有值）',
  `content`         varchar(2000) NOT NULL COMMENT 'TEXT/IMAGE 为文本或图片URL；PRODUCT/ORDER 为卡片 JSON',
  `status`          varchar(16)  NOT NULL DEFAULT 'SENT' COMMENT 'SENT/DELIVERED/READ',
  `client_msg_id`   varchar(64)  DEFAULT NULL COMMENT '客户端幂等ID；卡片消息写死为 CARD:{orderNo}:{cardType}',
  `create_time`     datetime     NOT NULL,
  `update_time`     datetime     NOT NULL COMMENT '至少 M5 置已读时会更新',
  `deleted`         tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_msg` (`from_id`, `client_msg_id`),
  KEY `idx_conv_id` (`conversation_id`, `id`),
  KEY `idx_to` (`to_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

CREATE TABLE `t_notice` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`     bigint unsigned NOT NULL,
  `type`        varchar(16)  NOT NULL COMMENT 'SYSTEM/ORDER/REFUND（v1.1 起无 AUDIT）',
  `title`       varchar(100) NOT NULL,
  `content`     varchar(500) NOT NULL,
  `biz_id`      varchar(64)  DEFAULT NULL COMMENT '关联业务ID（订单号 / 商品ID / 用户ID）',
  `biz_type`    varchar(16)  DEFAULT NULL COMMENT '关联业务类型 ORDER/PRODUCT/USER/NONE，M6 前端据此决定点击跳哪里',
  `read_flag`   tinyint      NOT NULL DEFAULT 0,
  `create_time` datetime     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `read_flag`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知表';

-- ------------------------------------------------------------
-- mall_product : Seata AT 回滚日志表
-- ------------------------------------------------------------
USE `mall_product`;

CREATE TABLE `undo_log` (
  `branch_id`     bigint       NOT NULL COMMENT '分支事务ID',
  `xid`           varchar(128) NOT NULL COMMENT '全局事务ID',
  `context`       varchar(128) NOT NULL COMMENT '序列化上下文',
  `rollback_info` longblob     NOT NULL COMMENT '回滚快照',
  `log_status`    int          NOT NULL COMMENT '0正常 1已提交',
  `log_created`   datetime(6)  NOT NULL,
  `log_modified`  datetime(6)  NOT NULL,
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata AT 回滚日志';

-- ------------------------------------------------------------
-- mall_order : Seata AT 回滚日志表
-- ------------------------------------------------------------
USE `mall_order`;

CREATE TABLE `undo_log` (
  `branch_id`     bigint       NOT NULL COMMENT '分支事务ID',
  `xid`           varchar(128) NOT NULL COMMENT '全局事务ID',
  `context`       varchar(128) NOT NULL COMMENT '序列化上下文',
  `rollback_info` longblob     NOT NULL COMMENT '回滚快照',
  `log_status`    int          NOT NULL COMMENT '0正常 1已提交',
  `log_created`   datetime(6)  NOT NULL,
  `log_modified`  datetime(6)  NOT NULL,
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata AT 回滚日志';

