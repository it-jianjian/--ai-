package com.jianjian.ai.zksh.report.domain.dto;

public record CreateReportTaskResponse(
        String taskId,
        String status,
        String stage
) {
}

