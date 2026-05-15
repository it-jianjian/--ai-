package com.jianjian.ai.zksh.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiChatMessageEntity {
    private Long id;
    private Long sessionId;
    private Long userId;
    private String role;
    private String content;
    private String riskLevel;
    private Integer tokens;
    private LocalDateTime createdAt;
}
