package com.jianjian.ai.zksh.mapper;

import com.jianjian.ai.zksh.domain.entity.NotificationEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationMapper {
    int insert(NotificationEntity entity);

    List<NotificationEntity> selectByUserId(@Param("userId") Long userId, @Param("isRead") Integer isRead);

    List<NotificationEntity> selectUnreadAfterId(@Param("userId") Long userId, @Param("lastId") Long lastId, @Param("limit") Integer limit);

    int countByUserTypeAndContent(@Param("userId") Long userId, @Param("type") String type, @Param("content") String content);

    int readById(@Param("id") Long id, @Param("userId") Long userId);
}
