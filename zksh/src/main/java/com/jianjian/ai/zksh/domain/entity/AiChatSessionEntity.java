package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiChatSessionEntity {
    private Long id;
    private Long userId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
}
