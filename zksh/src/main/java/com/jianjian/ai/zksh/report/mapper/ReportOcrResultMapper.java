package com.jianjian.ai.zksh.report.mapper;

import com.jianjian.ai.zksh.report.domain.entity.ReportOcrResultEntity;
import org.apache.ibatis.annotations.Param;

public interface ReportOcrResultMapper {
    int insert(ReportOcrResultEntity entity);

    ReportOcrResultEntity selectByTaskId(@Param("taskId") String taskId);
}

