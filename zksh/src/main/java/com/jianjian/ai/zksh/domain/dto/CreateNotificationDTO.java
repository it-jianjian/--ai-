package com.jianjian.ai.zksh.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateNotificationDTO(
        @NotBlank(message = "通知标题不能为空")
        @Size(max = 100, message = "通知标题长度不能超过100")
        String title,
        @NotBlank(message = "通知内容不能为空")
        @Size(max = 500, message = "通知内容长度不能超过500")
        String content,
        @Size(max = 20, message = "通知类型长度不能超过20")
        String type,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime notifyTime
) {
}
