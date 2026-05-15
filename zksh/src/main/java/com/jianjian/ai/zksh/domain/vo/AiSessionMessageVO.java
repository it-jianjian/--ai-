package com.jianjian.ai.zksh.domain.vo;

import java.time.LocalDateTime;

public record AiSessionMessageVO(
        Long messageId,
        String role,
        String content,
        String riskLevel,
        Integer tokens,
        LocalDateTime createdAt
) {
}
