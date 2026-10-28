package com.campus.mall.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/** P1 列表 / P3 我的商品 / P24 后台列表的查询参数。 */
@Data
public class ProductQuery {

    private String keyword;
    private Long categoryId;
    private String city;
    /** new / oldest / price_asc / price_desc / hot / nearby */
    private String sort;
    /** 成色下限，7 表示只看出 7 成新及以上 */
    private Integer conditionLevel;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    /** 我的商品/后台：按状态过滤 */
    private String status;
    /** 仅后台：按卖家过滤 */
    private Long sellerId;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
