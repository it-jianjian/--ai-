package com.jianjian.ai.zksh.domain.dto;

import jakarta.validation.constraints.Size;

public record CreateAiChatSessionDTO(
        @Size(max = 100, message = "会话标题长度不能超过100")
        String title
) {
}
