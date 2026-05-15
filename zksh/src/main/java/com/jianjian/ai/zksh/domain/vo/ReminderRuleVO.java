package com.jianjian.ai.zksh.domain.vo;

import java.time.LocalDateTime;

public record ReminderRuleVO(
        Long id,
        String title,
        String content,
        String type,
        Integer intervalMinutes,
        Integer enabled,
        LocalDateTime nextTriggerTime
) {
}
