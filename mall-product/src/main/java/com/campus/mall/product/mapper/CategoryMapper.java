package com.campus.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.product.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 物理删除。uk_parent_name(parent_id,name,deleted) 的 deleted 参与唯一键，
     * 同名分类被软删第二次会撞键，所以删除走物理删（被商品/子分类引用时业务层已先拦住）。
     */
    @Delete("DELETE FROM t_category WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);

    /**
     * 清掉同名软删残留。uk_parent_name(parent_id,name,deleted) 里 deleted 参与唯一键，
     * 不先清掉 (parent,name,1) 的旧行，新建/改名到同名就会撞键。
     */
    @Delete("DELETE FROM t_category WHERE parent_id = #{parentId} AND name = #{name} AND deleted = 1")
    int purgeSoftDeleted(@Param("parentId") Long parentId, @Param("name") String name);
}
