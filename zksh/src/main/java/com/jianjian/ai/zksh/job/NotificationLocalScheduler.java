package com.jianjian.ai.zksh.job;

import com.jianjian.ai.zksh.service.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notify", name = "local-schedule-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationLocalScheduler {

    private final NotificationService notificationService;

    public NotificationLocalScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${app.notify.schedule-ms:60000}")
    public void runDueRules() {
        notificationService.executeDueRules();
    }

    @Scheduled(fixedDelayString = "${app.notify.schedule-ms:60000}", initialDelay = 15000)
    public void runAbnormalAlerts() {
        notificationService.executeAbnormalMetricAlerts();
    }
}
