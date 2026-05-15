package com.jianjian.ai.zksh.report.mapper;

import com.jianjian.ai.zksh.report.domain.entity.ReportInterpretationEntity;
import org.apache.ibatis.annotations.Param;

public interface ReportInterpretationMapper {
    int insert(ReportInterpretationEntity entity);

    ReportInterpretationEntity selectByTaskId(@Param("taskId") String taskId);
}

