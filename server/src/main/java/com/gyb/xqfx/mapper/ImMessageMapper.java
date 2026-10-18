package com.gyb.xqfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gyb.xqfx.entity.ImMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 站内通讯 Mapper
 */
public interface ImMessageMapper extends BaseMapper<ImMessage> {

    /**
     * 会话汇总：与我相关的每个联系人 -> 最后消息时间 / 未读数
     *
     * @param me 当前登录用户ID
     */
    @Select("SELECT IF(sender_id = #{me}, receiver_id, sender_id) AS peerId, " +
            "       MAX(create_time) AS lastTime, " +
            "       SUM(CASE WHEN receiver_id = #{me} AND is_read = 0 THEN 1 ELSE 0 END) AS unreadCount " +
            "FROM im_message " +
            "WHERE is_deleted = 0 AND (sender_id = #{me} OR receiver_id = #{me}) " +
            "GROUP BY peerId " +
            "ORDER BY lastTime DESC")
    List<Map<String, Object>> conversations(@Param("me") Long me);

    /**
     * 与某人的最近一条消息（用于会话列表摘要）
     */
    @Select("SELECT * FROM im_message " +
            "WHERE is_deleted = 0 " +
            "  AND ((sender_id = #{me} AND receiver_id = #{peer}) " +
            "    OR (sender_id = #{peer} AND receiver_id = #{me})) " +
            "ORDER BY create_time DESC LIMIT 1")
    ImMessage lastMessage(@Param("me") Long me, @Param("peer") Long peer);
}
