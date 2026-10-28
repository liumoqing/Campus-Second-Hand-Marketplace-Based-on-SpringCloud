package com.campus.mall.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.message.entity.Notice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    @Select("SELECT COUNT(*) FROM t_notice WHERE user_id=#{userId} AND read_flag=0")
    long unreadCount(@Param("userId") Long userId);

    @Update("UPDATE t_notice SET read_flag=1 WHERE user_id=#{userId} AND read_flag=0")
    int markAllRead(@Param("userId") Long userId);

    @Update("<script>UPDATE t_notice SET read_flag=1 WHERE user_id=#{userId} AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int markRead(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
