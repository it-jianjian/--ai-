package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HealthRecordEntity {
    private Long id;
    private Long userId;
    private String metricType;
    private String metricValue;
    private String unit;
    private LocalDateTime recordTime;
    private String remark;
    private LocalDateTime createdAt;
}
