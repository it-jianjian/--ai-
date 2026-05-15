package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationEntity {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String type;
    private Integer isRead;
    private LocalDateTime notifyTime;
    private LocalDateTime createdAt;
}
