package com.campus.mall.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.message.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /** 首屏：最新 size 条（前端倒序渲染后 reverse） */
    @Select("SELECT * FROM t_message WHERE conversation_id=#{conversationId} AND deleted=0 "
            + "ORDER BY id DESC LIMIT #{size}")
    List<Message> historyFirst(@Param("conversationId") Long conversationId, @Param("size") int size);

    /** 上拉更多：比 lastId 更早的 size 条 */
    @Select("SELECT * FROM t_message WHERE conversation_id=#{conversationId} AND deleted=0 AND id < #{lastId} "
            + "ORDER BY id DESC LIMIT #{size}")
    List<Message> historyBefore(@Param("conversationId") Long conversationId,
                               @Param("lastId") Long lastId, @Param("size") int size);

    /** 把会话里发给我的、id ≤ lastMsgId 的消息置为已读 */
    @Update("UPDATE t_message SET status='READ', update_time=#{now} "
            + "WHERE conversation_id=#{conversationId} AND to_id=#{userId} AND id<=#{lastMsgId} AND status<>'READ' AND deleted=0")
    int markReadUpTo(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                     @Param("lastMsgId") Long lastMsgId, @Param("now") LocalDateTime now);
}
