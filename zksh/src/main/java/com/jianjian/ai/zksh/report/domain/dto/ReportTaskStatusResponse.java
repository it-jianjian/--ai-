package com.jianjian.ai.zksh.report.domain.dto;

import com.jianjian.ai.zksh.report.domain.vo.ReportTaskStepLogVO;

import java.util.List;

public record ReportTaskStatusResponse(
        String taskId,
        String status,
        String stage,
        String errorMessage,
        String summary,
        List<ReportTaskStepLogVO> stepLogs
) {
}

