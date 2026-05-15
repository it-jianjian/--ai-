package com.jianjian.ai.zksh.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatMessageRequestDTO(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容长度不能超过2000")
        String content
) {
}
