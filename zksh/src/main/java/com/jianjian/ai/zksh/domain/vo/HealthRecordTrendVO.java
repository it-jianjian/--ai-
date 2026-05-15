package com.jianjian.ai.zksh.domain.vo;

import java.time.LocalDateTime;

public record HealthRecordTrendVO(
        String metricType,
        String metricValue,
        LocalDateTime recordTime,
        String riskLevel
) {
}
