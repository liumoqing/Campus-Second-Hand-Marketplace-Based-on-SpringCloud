package com.campus.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单 Mapper。状态迁移一律用"带前置状态的条件 UPDATE"，影响 0 行即说明别人已经改过（并发安全）。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /** 支付成功：UNPAID → UNSHIPPED（记 pay_type / pay_time） */
    @Update("UPDATE t_order SET status='UNSHIPPED', pay_type=#{payType}, pay_time=#{now}, update_time=#{now} "
            + "WHERE order_no=#{orderNo} AND status='UNPAID' AND deleted=0")
    int markPaid(@Param("orderNo") String orderNo, @Param("payType") String payType, @Param("now") LocalDateTime now);

    /** 发货：UNSHIPPED → UNRECEIVED */
    @Update("UPDATE t_order SET status='UNRECEIVED', ship_time=#{now}, logistics_no=#{logisticsNo}, update_time=#{now} "
            + "WHERE order_no=#{orderNo} AND status='UNSHIPPED' AND deleted=0")
    int ship(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now, @Param("logisticsNo") String logisticsNo);

    /** 确认收货：UNRECEIVED → COMPLETED（同时记 finish_time，售后期从这一刻起算 7 天） */
    @Update("UPDATE t_order SET status='COMPLETED', receive_time=#{now}, finish_time=#{now}, update_time=#{now} "
            + "WHERE order_no=#{orderNo} AND status='UNRECEIVED' AND deleted=0")
    int receive(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now);

    /** 取消：UNPAID → CANCELLED */
    @Update("UPDATE t_order SET status='CANCELLED', cancel_time=#{now}, cancel_reason=#{reason}, update_time=#{now} "
            + "WHERE order_no=#{orderNo} AND status='UNPAID' AND deleted=0")
    int cancelUnpaid(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now, @Param("reason") String reason);

    /** 申请退款/售后：可申请状态 → REFUNDING */
    @Update("UPDATE t_order SET status='REFUNDING', update_time=#{now} WHERE order_no=#{orderNo} "
            + "AND status=#{before} AND deleted=0")
    int toRefunding(@Param("orderNo") String orderNo, @Param("before") String before, @Param("now") LocalDateTime now);

    /** 拒绝退款：REFUNDING → 申请前的状态 */
    @Update("UPDATE t_order SET status=#{back}, update_time=#{now} WHERE order_no=#{orderNo} AND status='REFUNDING' AND deleted=0")
    int rejectRefund(@Param("orderNo") String orderNo, @Param("back") String back, @Param("now") LocalDateTime now);

    /** 退款完成：REFUNDING → REFUNDED */
    @Update("UPDATE t_order SET status='REFUNDED', update_time=#{now} WHERE order_no=#{orderNo} AND status='REFUNDING' AND deleted=0")
    int refunded(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now);

    @Update("UPDATE t_order SET buyer_reviewed=1, update_time=#{now} WHERE id=#{orderId} AND buyer_reviewed=0")
    int markBuyerReviewed(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    @Update("UPDATE t_order SET seller_reviewed=1, update_time=#{now} WHERE id=#{orderId} AND seller_reviewed=0")
    int markSellerReviewed(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /** 某个商品是否还有"未结束"的订单（LOCKED 商品回补前的判断） */
    @Select("SELECT COUNT(*) FROM t_order WHERE product_id=#{productId} AND deleted=0 "
            + "AND status IN ('UNPAID','UNSHIPPED','UNRECEIVED','REFUNDING','COMPLETED')")
    long existsActiveByProduct(@Param("productId") Long productId);

    /** 用户维度统计：卖出/买到/评价/待发货/待收货，一条 SQL 算完（03 §11） */
    @Select("SELECT "
            + " SUM(CASE WHEN seller_id=#{userId} AND status='COMPLETED' THEN 1 ELSE 0 END) AS soldCount, "
            + " SUM(CASE WHEN buyer_id=#{userId} AND status='COMPLETED' THEN 1 ELSE 0 END) AS boughtCount, "
            + " SUM(CASE WHEN seller_id=#{userId} AND status='UNSHIPPED' THEN 1 ELSE 0 END) AS pendingShipCount, "
            + " SUM(CASE WHEN buyer_id=#{userId} AND status='UNRECEIVED' THEN 1 ELSE 0 END) AS pendingReceiveCount "
            + "FROM t_order WHERE deleted=0 AND (buyer_id=#{userId} OR seller_id=#{userId})")
    Map<String, Object> statistics(@Param("userId") Long userId);
}
