-- ============================================================
-- 晴川二手市场 · 02-init-data.sql
-- 演示数据：4 个账号（1 超管 / 1 管理员 / 2 普通用户）+ 两级分类 + 3 个示例商品
-- 用法：mysql --host=127.0.0.1 --port=3306 --user=root --password=123456 < sql/02-init-data.sql
-- 明文密码统一 Qingchuan@2026（BCrypt cost=10，由 hutool BCrypt 生成并已校验通过）
-- ============================================================

USE `mall_user`;

-- ⚠️ 超级管理员：全系统唯一，只能由本脚本插入；注册接口与后台接口都不许创建该角色（03 §A2 / U17）
-- id 写死为 1/2/3/4，方便记忆与联调
INSERT INTO `t_user`
  (`id`,`username`,`password`,`nickname`,`avatar`,`email`,`role`,`status`,`can_publish`,
   `province`,`city`,`district`,`signature`,`credit_score`,`create_time`,`update_time`)
VALUES
  (1,'superadmin','$2a$10$E.nVOgkP2pK3dJ4JqKqNvePqLpwpUvg4YL32.b2MRrkGOp0notshC','超级管理员',
     '/static/avatar/superadmin.png','superadmin@qingchuan.local','SUPER_ADMIN','NORMAL',1,
     '湖北省','武汉市','江岸区','平台超级管理员',100,NOW(),NOW()),
  (2,'admin','$2a$10$E.nVOgkP2pK3dJ4JqKqNvePqLpwpUvg4YL32.b2MRrkGOp0notshC','平台管理员',
     '/static/avatar/admin.png','admin@qingchuan.local','ADMIN','NORMAL',1,
     '湖北省','武汉市','江岸区','维护秩序，也修自行车',100,NOW(),NOW()),
  (3,'alice','$2a$10$E.nVOgkP2pK3dJ4JqKqNvePqLpwpUvg4YL32.b2MRrkGOp0notshC','爱丽丝',
     '/static/avatar/alice.png','alice@qingchuan.local','USER','NORMAL',1,
     '湖北省','武汉市','洪山区','毕业清仓，可小刀',100,NOW(),NOW()),
  (4,'bob','$2a$10$E.nVOgkP2pK3dJ4JqKqNvePqLpwpUvg4YL32.b2MRrkGOp0notshC','小鲍',
     '/static/avatar/bob.png','bob@qingchuan.local','USER','NORMAL',1,
     '湖北省','武汉市','江夏区','只卖自用，不议价',98,NOW(),NOW());

-- 收货地址（给 alice / bob 各一条，购买页的横向卡片直接有数据）
USE `mall_user`;
INSERT INTO `t_user_address`
  (`id`,`user_id`,`receiver`,`phone`,`province`,`city`,`district`,`detail`,`is_default`,`create_time`,`update_time`)
VALUES
  (1,3,'爱丽丝','13800000001','湖北省','武汉市','洪山区','洪山街道某小区 3 栋 502',1,NOW(),NOW()),
  (2,4,'小鲍','13800000002','湖北省','武汉市','江夏区','江夏大道 88 号 A 座 1201',1,NOW(),NOW());

-- ------------------------------------------------------------
USE `mall_product`;

-- 两级分类：管理员可在后台增删（03 §P19~P23），有商品的分类不能删（30007）
INSERT INTO `t_category`
  (`id`,`parent_id`,`name`,`icon`,`sort`,`status`,`create_time`,`update_time`)
VALUES
  (1,0,'数码电子','📱',1,1,NOW(),NOW()),
  (2,1,'手机','',1,1,NOW(),NOW()),
  (3,1,'笔记本电脑','',2,1,NOW(),NOW()),
  (4,1,'平板与配件','',3,1,NOW(),NOW()),
  (5,0,'书籍教材','📚',2,1,NOW(),NOW()),
  (6,5,'专业教材','',1,1,NOW(),NOW()),
  (7,5,'文学小说','',2,1,NOW(),NOW()),
  (8,0,'生活用品','🛋',3,1,NOW(),NOW()),
  (9,8,'小家电','',1,1,NOW(),NOW()),
  (10,0,'运动户外','🚴',4,1,NOW(),NOW()),
  (11,0,'服饰鞋包','👕',5,1,NOW(),NOW());

-- 示例商品：发布即上架（无审核），status=ON_SALE；运费模式三选一
-- 图片地址用占位图，前端 <img> 加载失败时显示占位样式即可
INSERT INTO `t_product`
  (`id`,`seller_id`,`category_id`,`title`,`description`,`price`,`original_price`,`condition_level`,`cover_url`,
   `province`,`city`,`district`,`freight_mode`,`status`,`view_count`,`favorite_count`,`comment_count`,
   `create_time`,`update_time`)
VALUES
  (1,3,2,'iPhone 13 128G 星光色 国行',
     '2022 年买的，一直带壳带膜，电池健康 88%，无维修无拆修，功能全正常。附原装充电线和盒子。','2699.00','5999.00',9,
     'https://placehold.co/800x800?text=iPhone+13',
     '湖北省','武汉市','洪山区','DISTANCE','ON_SALE',128,12,1,NOW(),NOW()),
  (2,4,3,'ThinkPad X1 Carbon 2020 i7 16G',
     '自用办公本，键盘手感好，屏幕无亮点无坏点。电池能撑 5 小时左右。买来换了硬盘，现在 1T。','3200.00','9999.00',8,
     'https://placehold.co/800x800?text=ThinkPad',
     '湖北省','武汉市','江夏区','FREE_SHIPPING','ON_SALE',86,7,0,NOW(),NOW()),
  (3,3,6,'《数据结构与算法分析》C 语言版 第二版',
     '课程用完，只有前三章有少量铅笔笔记，其余全新。考研 408 可以看。','25.00','59.00',8,
     'https://placehold.co/800x800?text=Book',
     '湖北省','武汉市','洪山区','SELF_PICKUP','ON_SALE',42,3,0,NOW(),NOW());

INSERT INTO `t_product_image` (`product_id`,`url`,`sort`,`create_time`)
VALUES
  (1,'https://placehold.co/800x800?text=iPhone+13+1',1,NOW()),
  (1,'https://placehold.co/800x800?text=iPhone+13+2',2,NOW()),
  (2,'https://placehold.co/800x800?text=ThinkPad+1',1,NOW()),
  (3,'https://placehold.co/800x800?text=Book+1',1,NOW());

-- 商品评论区：不带 parent_id 的一级评论（parent_id=0）
INSERT INTO `t_product_comment`
  (`id`,`product_id`,`user_id`,`parent_id`,`content`,`status`,`create_time`,`update_time`)
VALUES
  (1,1,4,0,'请问支持验机吗？想当面看下电池循环。',1,NOW(),NOW());
