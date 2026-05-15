package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReminderRuleEntity {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String type;
    private Integer intervalMinutes;
    private Integer enabled;
    private LocalDateTime nextTriggerTime;
    private LocalDateTime createdAt;
}
