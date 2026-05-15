package com.jianjian.ai.zksh.tool;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.dto.QueryHealthRecordDTO;
import com.jianjian.ai.zksh.domain.vo.HealthRecordVO;
import com.jianjian.ai.zksh.domain.vo.HealthSummaryVO;
import com.jianjian.ai.zksh.domain.vo.NotificationVO;
import com.jianjian.ai.zksh.security.UserContext;
import com.jianjian.ai.zksh.service.HealthRecordService;
import com.jianjian.ai.zksh.service.NotificationService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Function Calling 工具集合（V1）：优先提供只读能力，避免误操作。
 */
@Component("healthAiTools")
public class HealthAiTools {

    private final HealthRecordService healthRecordService;
    private final NotificationService notificationService;

    public HealthAiTools(HealthRecordService healthRecordService, NotificationService notificationService) {
        this.healthRecordService = healthRecordService;
        this.notificationService = notificationService;
    }

    @Tool("获取当前登录用户的健康摘要，包括记录总数、异常条数和最近一次指标")
    public HealthSummaryVO getMyHealthSummary() {
        Long userId = requiredUserId();
        return healthRecordService.summary(userId);
    }

    @Tool("查询当前登录用户最近的健康记录。metricType 可为空，limit 建议 1-20")
    public List<HealthRecordVO> getMyRecentHealthRecords(String metricType, Integer limit) {
        Long userId = requiredUserId();
        List<HealthRecordVO> all = healthRecordService.list(userId, new QueryHealthRecordDTO(metricType, null, null));
        int effectiveLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 20);
        return all.stream().limit(effectiveLimit).toList();
    }

    @Tool("查询当前登录用户的未读通知列表")
    public List<NotificationVO> getMyUnreadNotifications() {
        Long userId = requiredUserId();
        return notificationService.list(userId, 0);
    }

    @Tool("获取系统当前时间（ISO 本地时间）")
    public String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }

    private Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException("工具调用失败：未获取到登录用户");
        }
        return userId;
    }
}

