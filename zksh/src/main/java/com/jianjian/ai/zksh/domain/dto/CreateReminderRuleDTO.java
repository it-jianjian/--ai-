package com.jianjian.ai.zksh.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReminderRuleDTO(
        @NotBlank(message = "规则标题不能为空")
        @Size(max = 100, message = "规则标题长度不能超过100")
        String title,
        @NotBlank(message = "规则内容不能为空")
        @Size(max = 500, message = "规则内容长度不能超过500")
        String content,
        @Size(max = 20, message = "规则类型长度不能超过20")
        String type,
        @Min(value = 1, message = "提醒间隔分钟必须大于0")
        Integer intervalMinutes
) {
}
