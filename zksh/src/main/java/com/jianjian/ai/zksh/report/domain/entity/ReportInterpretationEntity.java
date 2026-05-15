package com.jianjian.ai.zksh.report.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportInterpretationEntity {
    private Long id;
    private String taskId;
    private String summary;
    private String structuredJson;
    private LocalDateTime createdAt;
}

