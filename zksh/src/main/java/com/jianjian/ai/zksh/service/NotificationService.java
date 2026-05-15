package com.jianjian.ai.zksh.service;

import com.jianjian.ai.zksh.domain.dto.CreateNotificationDTO;
import com.jianjian.ai.zksh.domain.dto.CreateReminderRuleDTO;
import com.jianjian.ai.zksh.domain.vo.NotificationVO;
import com.jianjian.ai.zksh.domain.vo.ReminderRuleVO;

import java.util.List;

public interface NotificationService {
    NotificationVO create(Long userId, CreateNotificationDTO dto);

    List<NotificationVO> list(Long userId, Integer isRead);

    List<NotificationVO> listUnreadAfterId(Long userId, Long lastId, Integer limit);

    void read(Long userId, Long id);

    ReminderRuleVO createRule(Long userId, CreateReminderRuleDTO dto);

    List<ReminderRuleVO> listRules(Long userId);

    void executeDueRules();

    void executeAbnormalMetricAlerts();
}
