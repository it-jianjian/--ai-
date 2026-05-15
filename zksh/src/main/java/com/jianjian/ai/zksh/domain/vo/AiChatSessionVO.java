package com.jianjian.ai.zksh.domain.vo;

import java.time.LocalDateTime;

public record AiChatSessionVO(
        Long sessionId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastMessageAt
) {
}
