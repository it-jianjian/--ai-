package com.jianjian.ai.zksh.report.domain.vo;

import java.time.LocalDateTime;

public record ReportTaskStepLogVO(
        String stepCode,
        String stepName,
        String status,
        String detail,
        LocalDateTime createdAt
) {
}

