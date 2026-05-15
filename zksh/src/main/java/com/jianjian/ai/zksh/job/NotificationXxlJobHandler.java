package com.jianjian.ai.zksh.job;

import com.jianjian.ai.zksh.service.NotificationService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
public class NotificationXxlJobHandler {

    private final NotificationService notificationService;

    public NotificationXxlJobHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @XxlJob("notifyDueRulesJob")
    public void notifyDueRulesJob() {
        notificationService.executeDueRules();
    }

    @XxlJob("notifyAbnormalMetricJob")
    public void notifyAbnormalMetricJob() {
        notificationService.executeAbnormalMetricAlerts();
    }
}
