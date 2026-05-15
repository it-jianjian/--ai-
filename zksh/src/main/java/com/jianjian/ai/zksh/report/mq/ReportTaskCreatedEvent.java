package com.jianjian.ai.zksh.report.mq;

public record ReportTaskCreatedEvent(
        String taskId,
        Long userId
) {
}

