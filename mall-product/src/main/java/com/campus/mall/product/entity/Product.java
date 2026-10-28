package com.campus.mall.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.mall.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品（02 §5.2 t_product）：一件一单，stock 恒为 1。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_product")
public class Product extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sellerId;
    private Long categoryId;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    /** 成色 1~10，默认 9 */
    private Integer conditionLevel;
    private String coverUrl;
    private String province;
    private String city;
    private String district;
    /** FREE_SHIPPING / DISTANCE / SELF_PICKUP */
    private String freightMode;
    /** DRAFT / ON_SALE / LOCKED / SOLD / OFF_SHELF */
    private String status;
    private String lockOrderNo;
    private String offShelfReason;
    private Integer viewCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Integer stock;
    private LocalDateTime soldTime;
    private Integer version;
}
