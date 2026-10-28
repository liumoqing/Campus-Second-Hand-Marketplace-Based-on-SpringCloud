package com.campus.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.order.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Map;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /** 被评价人的平均分与评价数（只算买家评卖家这一方向，卖家主页展示用） */
    @Select("SELECT ROUND(AVG(rating),2) AS avgRating, COUNT(*) AS reviewCount FROM t_review "
            + "WHERE reviewee_id=#{userId} AND direction='BUYER_TO_SELLER' AND deleted=0")
    Map<String, Object> ratingOf(@Param("userId") Long userId);

    /** 订单当前方向是否已评价 */
    @Select("SELECT COUNT(*) FROM t_review WHERE order_id=#{orderId} AND direction=#{direction} AND deleted=0")
    long existsByDirection(@Param("orderId") Long orderId, @Param("direction") String direction);
}
