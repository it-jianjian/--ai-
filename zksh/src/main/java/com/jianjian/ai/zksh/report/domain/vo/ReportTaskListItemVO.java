package com.jianjian.ai.zksh.report.domain.vo;

import java.time.LocalDateTime;

public record ReportTaskListItemVO(
        String taskId,
        String fileName,
        String status,
        String stage,
        String errorMessage,
        String summary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

