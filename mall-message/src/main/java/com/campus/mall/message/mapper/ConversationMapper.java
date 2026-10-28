package com.campus.mall.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.mall.message.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 我的会话列表（按最后消息时间倒序）。
     * 用 UNION ALL 走两条 idx_*_time 索引，避免 {@code user_a_id=? OR user_b_id=?} 退化成全表扫。
     */
    @Select("SELECT * FROM ("
            + " SELECT * FROM t_conversation WHERE user_a_id=#{userId}"
            + " UNION ALL"
            + " SELECT * FROM t_conversation WHERE user_b_id=#{userId}"
            + ") t ORDER BY last_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Conversation> pageOfUser(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ("
            + " SELECT id FROM t_conversation WHERE user_a_id=#{userId}"
            + " UNION ALL"
            + " SELECT id FROM t_conversation WHERE user_b_id=#{userId}"
            + ") t")
    long countOfUser(@Param("userId") Long userId);

    /** 新消息：四列同事务更新，未读按收件人 +1 */
    @Update("UPDATE t_conversation SET last_msg=#{summary}, last_msg_type=#{msgType}, last_card_type=#{cardType}, "
            + "last_time=#{now}, unread_a=unread_a + CASE WHEN user_a_id=#{toId} THEN 1 ELSE 0 END, "
            + "unread_b=unread_b + CASE WHEN user_b_id=#{toId} THEN 1 ELSE 0 END, update_time=#{now} "
            + "WHERE id=#{id}")
    int touch(@Param("id") Long id, @Param("summary") String summary, @Param("msgType") String msgType,
              @Param("cardType") String cardType, @Param("toId") Long toId, @Param("now") LocalDateTime now);

    /** 清某人未读（DB 兜底值） */
    @Update("UPDATE t_conversation SET unread_a=CASE WHEN user_a_id=#{userId} THEN 0 ELSE unread_a END, "
            + "unread_b=CASE WHEN user_b_id=#{userId} THEN 0 ELSE unread_b END, update_time=#{now} WHERE id=#{id}")
    int clearUnread(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);
}
