package com.jianjian.ai.zksh.domain.vo;

import java.time.LocalDateTime;

public record HealthRecordVO(
        Long id,
        String metricType,
        String metricValue,
        String unit,
        LocalDateTime recordTime,
        String remark,
        String riskLevel
) {
}
