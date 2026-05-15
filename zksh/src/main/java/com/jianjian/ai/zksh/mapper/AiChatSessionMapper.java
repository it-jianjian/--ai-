package com.jianjian.ai.zksh.mapper;

import com.jianjian.ai.zksh.domain.entity.AiChatSessionEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AiChatSessionMapper {
    int insert(AiChatSessionEntity entity);

    AiChatSessionEntity selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    List<AiChatSessionEntity> selectByUserId(@Param("userId") Long userId);

    int touchById(@Param("id") Long id, @Param("lastMessageAt") LocalDateTime lastMessageAt);

    int updateTitle(@Param("id") Long id, @Param("userId") Long userId, @Param("title") String title);

    int softDelete(@Param("id") Long id, @Param("userId") Long userId);
}
