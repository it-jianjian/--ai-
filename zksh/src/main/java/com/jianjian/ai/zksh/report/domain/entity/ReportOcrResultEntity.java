package com.jianjian.ai.zksh.report.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportOcrResultEntity {
    private Long id;
    private String taskId;
    private String ocrText;
    private String rawJson;
    private LocalDateTime createdAt;
}

