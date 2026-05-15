package com.jianjian.ai.zksh.domain.vo;

import java.util.List;

public record AiChatMessageResponseVO(
        String messageId,
        String answer,
        String riskLevel,
        List<AiReferenceVO> references,
        Integer tokens
) {
}
