package com.campus.mall.user.vo;

import lombok.Data;

/**
 * U4 我的统计（03 §3）。
 */
@Data
public class MyStatisticsVO {

    private Long onSaleCount = 0L;
    private Long draftCount = 0L;
    private Long soldCount = 0L;
    private Long boughtCount = 0L;
    private Long favoriteCount = 0L;
    private Long pendingShipCount = 0L;
    private Long pendingReceiveCount = 0L;
    private Double avgRating = 0D;
}
