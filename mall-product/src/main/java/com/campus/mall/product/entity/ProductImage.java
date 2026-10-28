package com.campus.mall.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品图片（02 §5.3 t_product_image）：注意列名是 sort，不是 sort_order。 */
@Data
@TableName("t_product_image")
public class ProductImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private String url;
    private Integer sort;
    private LocalDateTime createTime;
}
