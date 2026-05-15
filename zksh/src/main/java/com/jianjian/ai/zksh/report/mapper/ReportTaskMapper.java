package com.jianjian.ai.zksh.report.mapper;

import com.jianjian.ai.zksh.report.domain.entity.ReportTaskEntity;
import com.jianjian.ai.zksh.report.domain.vo.ReportTaskListItemVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReportTaskMapper {
    int insert(ReportTaskEntity entity);

    ReportTaskEntity selectByTaskId(@Param("taskId") String taskId, @Param("userId") Long userId);

    ReportTaskEntity selectByTaskIdNoUser(@Param("taskId") String taskId);

    int updateStatusStage(
            @Param("taskId") String taskId,
            @Param("status") String status,
            @Param("stage") String stage,
            @Param("errorMessage") String errorMessage
    );

    int touchRetry(@Param("taskId") String taskId, @Param("retryCount") Integer retryCount);

    List<ReportTaskListItemVO> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);
}

