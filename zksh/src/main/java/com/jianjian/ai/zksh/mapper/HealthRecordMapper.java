package com.jianjian.ai.zksh.mapper;

import com.jianjian.ai.zksh.domain.entity.HealthRecordEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HealthRecordMapper {
    int insert(HealthRecordEntity entity);

    HealthRecordEntity selectById(@Param("id") Long id, @Param("userId") Long userId);

    List<HealthRecordEntity> selectByCondition(
            @Param("userId") Long userId,
            @Param("metricType") String metricType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<HealthRecordEntity> selectTrend(
            @Param("userId") Long userId,
            @Param("metricType") String metricType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    int updateById(HealthRecordEntity entity);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    long countByUserId(@Param("userId") Long userId);

    HealthRecordEntity selectLatestByUserId(@Param("userId") Long userId);

    List<HealthRecordEntity> selectRecentRecords(@Param("startTime") LocalDateTime startTime);
}
