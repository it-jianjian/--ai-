package com.jianjian.ai.zksh.report.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportTaskStepLogEntity {
    private Long id;
    private String taskId;
    private String stepCode;
    private String stepName;
    private String status;
    private String detail;
    private LocalDateTime createdAt;
}

