package com.jianjian.ai.zksh.report.mapper;

import com.jianjian.ai.zksh.report.domain.entity.ReportTaskStepLogEntity;
import com.jianjian.ai.zksh.report.domain.vo.ReportTaskStepLogVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReportTaskStepLogMapper {
    int insert(ReportTaskStepLogEntity entity);

    List<ReportTaskStepLogVO> selectByTaskId(@Param("taskId") String taskId);
}

