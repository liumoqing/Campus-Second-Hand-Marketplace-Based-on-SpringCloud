package com.campus.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper。计数与库存都用条件 UPDATE，避免读-改-写丢更新（02 §5.2 防超卖 SQL 的等价实现）。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /** P28 扣库存：只有 ON_SALE 且版本一致才锁定；返回受影响行数，0 表示被抢先 */
    @Update("UPDATE t_product SET status='LOCKED', stock=0, lock_order_no=#{orderNo}, version=version+1, "
            + "update_time=NOW() WHERE id=#{id} AND status='ON_SALE' AND version=#{version} AND deleted=0 "
            + "AND (lock_order_no IS NULL OR lock_order_no=#{orderNo})")
    int lock(@Param("id") Long id, @Param("version") int version, @Param("orderNo") String orderNo);

    /** P29 回补：按 lock_order_no 幂等回 ON_SALE */
    @Update("UPDATE t_product SET status='ON_SALE', stock=1, lock_order_no=NULL, version=version+1, "
            + "update_time=NOW() WHERE lock_order_no=#{orderNo} AND status='LOCKED'")
    int unlock(@Param("orderNo") String orderNo);

    /** P30 置已售 */
    @Update("UPDATE t_product SET status='SOLD', sold_time=NOW(), version=version+1, update_time=NOW() "
            + "WHERE lock_order_no=#{orderNo} AND status='LOCKED'")
    int markSold(@Param("orderNo") String orderNo);

    @Update("UPDATE t_product SET view_count=view_count+1 WHERE id=#{id}")
    int incrView(@Param("id") Long id);

    @Update("UPDATE t_product SET favorite_count=favorite_count+#{delta} WHERE id=#{id} "
            + "AND favorite_count+#{delta} >= 0")
    int incrFavorite(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE t_product SET comment_count=comment_count+#{delta} WHERE id=#{id} "
            + "AND comment_count+#{delta} >= 0")
    int incrComment(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 编辑页「发布」把草稿/已下架商品切回在售：顺手清掉下架原因，
     * updateById 会忽略 null 字段，所以这里单独写一条。
     */
    @Update("UPDATE t_product SET status='ON_SALE', off_shelf_reason=NULL, update_time=NOW() WHERE id=#{id}")
    int markOnSale(@Param("id") Long id);
}
