package com.campus.mall.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品域的视图对象。集中放一个文件里，用静态内部类，避免几十个小文件。
 */
public final class ProductVos {

    private ProductVos() {
    }

    /** 分类树节点（P19/P20） */
    @Data
    public static class CategoryNode {
        private Long id;
        private Long parentId;
        private String name;
        private String icon;
        private Integer sort;
        private Integer status;
        private List<CategoryNode> children;
    }

    /** 列表卡片（P1/P3/P17/P24 共用） */
    @Data
    public static class ProductCardVO {
        private Long id;
        private Long categoryId;
        private String title;
        private String coverUrl;
        private BigDecimal price;
        private BigDecimal originalPrice;
        private Integer conditionLevel;
        private String city;
        private String freightMode;
        private String status;
        private Long sellerId;
        private String sellerNickname;
        private String sellerAvatar;
        private Integer favoriteCount;
        private Integer commentCount;
        private Integer viewCount;
        private LocalDateTime createTime;
    }

    /** 详情页卖家卡片（7 个展示位由 mall-user 组装，这里只承载商品服务能拿到的部分） */
    @Data
    public static class SellerVO {
        private Long id;
        private String username;
        private String nickname;
        private String avatar;
        private String city;
        private String signature;
        private Integer creditScore;
        private LocalDateTime joinedAt;
        private Long registerDays;
        private LocalDateTime lastLoginTime;
        private Integer soldCount;
        private Integer onSaleCount;
        private BigDecimal avgRating;
        private Integer reviewCount;
    }

    /** P2 商品详情 */
    @Data
    public static class ProductDetailVO {
        private Long id;
        /** 卖家 ID：前端判断本人商品、卖家卡片跳转使用 */
        private Long sellerId;
        private String title;
        private String description;
        private Long categoryId;
        private String categoryName;
        private BigDecimal price;
        private BigDecimal originalPrice;
        private Integer conditionLevel;
        private String coverUrl;
        private List<String> images;
        private String province;
        private String city;
        private String district;
        private String freightMode;
        /** 给前端的运费提示文案：包邮/同城 5 元/跨城 10 元/仅自提 */
        private String freightHint;
        private String status;
        private String offShelfReason;
        private Integer viewCount;
        private Integer favoriteCount;
        private Integer commentCount;
        private Boolean favorited;
        private LocalDateTime createTime;
        private SellerVO seller;
    }

    /** 评论里嵌的用户小卡片 */
    @Data
    public static class CommentUserVO {
        private Long id;
        private String nickname;
        private String avatar;
    }

    /** P12 评论列表项（一级评论带 replies；回复项 replies 为 null） */
    @Data
    public static class CommentVO {
        private Long id;
        private Long parentId;
        private String content;
        private List<String> images;
        private CommentUserVO user;
        private Long replyCount;
        private List<CommentVO> replies;
        private LocalDateTime createTime;
    }

    /** P34 商品统计（"我的"页四个角标） */
    @Data
    public static class StatisticsVO {
        private Integer onSaleCount;
        private Integer draftCount;
        private Integer soldCount;
        private Integer favoriteCount;
    }

    /** P27 内部详情（订单服务锁定商品时用） */
    @Data
    public static class InnerProductVO {
        private Long id;
        private Long sellerId;
        private String title;
        private String coverUrl;
        private BigDecimal price;
        private String status;
        private Integer stock;
        private Integer version;
        private String city;
        private String freightMode;
    }

    /** P31 商品卡片（订单/消息服务用） */
    @Data
    public static class ProductBriefVO {
        private Long id;
        private String title;
        private String coverUrl;
        private BigDecimal price;
        private String status;
    }
}
