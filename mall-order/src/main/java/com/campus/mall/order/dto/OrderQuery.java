package com.campus.mall.order.dto;

import lombok.Data;

/** O2/O16 订单列表查询。 */
@Data
public class OrderQuery {

    /** bought=我买到的（默认） / sold=我卖出的 / all */
    private String role = "bought";
    /** UNPAID/UNSHIPPED/UNRECEIVED/COMPLETED/CANCELLED/REFUNDING/REFUNDED */
    private String status;
    private String keyword;
    /** 仅后台：按买家/卖家过滤 */
    private Long buyerId;
    private Long sellerId;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
