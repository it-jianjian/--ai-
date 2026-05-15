package com.jianjian.ai.zksh.domain.vo;

public record HealthSummaryVO(
        long totalRecords,
        long abnormalRecords,
        String latestMetricType,
        String latestMetricValue,
        String latestRecordTime
) {
}
