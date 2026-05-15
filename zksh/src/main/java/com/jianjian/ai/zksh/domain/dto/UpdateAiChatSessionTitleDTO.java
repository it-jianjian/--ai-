package com.jianjian.ai.zksh.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAiChatSessionTitleDTO(
        @NotBlank(message = "会话名称不能为空")
        @Size(max = 100, message = "会话名称长度不能超过100")
        String title
) {
}
