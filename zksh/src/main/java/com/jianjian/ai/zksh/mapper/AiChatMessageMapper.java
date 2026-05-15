package com.jianjian.ai.zksh.mapper;

import com.jianjian.ai.zksh.domain.entity.AiChatMessageEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AiChatMessageMapper {
    int insert(AiChatMessageEntity entity);

    List<AiChatMessageEntity> selectBySessionId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    int countBySessionId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    int deleteBySessionId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);
}
