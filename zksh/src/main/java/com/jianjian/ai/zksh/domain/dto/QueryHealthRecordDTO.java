package com.jianjian.ai.zksh.domain.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record QueryHealthRecordDTO(
        String metricType,
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime startTime,
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime endTime
) {
}
