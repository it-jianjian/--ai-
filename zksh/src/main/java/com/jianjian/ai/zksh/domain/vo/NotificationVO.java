package com.jianjian.ai.zksh.domain.vo;

import java.time.LocalDateTime;

public record NotificationVO(
        Long id,
        String title,
        String content,
        String type,
        Integer isRead,
        LocalDateTime notifyTime
) {
}
