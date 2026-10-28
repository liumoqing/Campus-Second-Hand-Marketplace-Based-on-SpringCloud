package com.campus.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.product.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}