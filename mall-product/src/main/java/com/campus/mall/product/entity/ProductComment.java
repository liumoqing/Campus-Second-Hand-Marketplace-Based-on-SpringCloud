package com.campus.mall.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.mall.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 商品评论（02 §5.4 t_product_comment）：一级 + 回复一层，status 管管理员隐藏。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_product_comment")
public class ProductComment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private Long userId;
    /** 0 为一级评论 */
    private Long parentId;
    private String content;
    /** JSON 数组字符串，如 ["http://.../a.jpg"] */
    private String images;
    /** 1 正常 0 管理员隐藏 */
    private Integer status;
}
