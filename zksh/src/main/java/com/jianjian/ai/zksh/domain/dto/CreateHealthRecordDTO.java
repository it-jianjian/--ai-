package com.jianjian.ai.zksh.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateHealthRecordDTO(
        @NotBlank(message = "指标类型不能为空")
        @Size(max = 32, message = "指标类型长度不能超过32")
        String metricType,
        @NotBlank(message = "指标值不能为空")
        @Size(max = 64, message = "指标值长度不能超过64")
        String metricValue,
        @Size(max = 16, message = "单位长度不能超过16")
        String unit,
        @NotNull(message = "记录时间不能为空")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime recordTime,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
