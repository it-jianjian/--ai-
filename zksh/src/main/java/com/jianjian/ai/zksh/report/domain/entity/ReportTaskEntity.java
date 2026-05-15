package com.jianjian.ai.zksh.report.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportTaskEntity {
    private Long id;
    private String taskId;
    private Long userId;
    private String fileName;
    private String filePath;
    private String status;
    private String stage;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

