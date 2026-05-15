package com.jianjian.ai.zksh.service.impl;

import com.jianjian.ai.zksh.domain.dto.CreateNotificationDTO;
import com.jianjian.ai.zksh.domain.dto.CreateReminderRuleDTO;
import com.jianjian.ai.zksh.domain.entity.HealthRecordEntity;
import com.jianjian.ai.zksh.domain.entity.NotificationEntity;
import com.jianjian.ai.zksh.domain.entity.ReminderRuleEntity;
import com.jianjian.ai.zksh.domain.vo.NotificationVO;
import com.jianjian.ai.zksh.domain.vo.ReminderRuleVO;
import com.jianjian.ai.zksh.mapper.HealthRecordMapper;
import com.jianjian.ai.zksh.mapper.NotificationMapper;
import com.jianjian.ai.zksh.mapper.ReminderRuleMapper;
import com.jianjian.ai.zksh.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final String AUTO_ALERT_TYPE = "health_alert_auto";


    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private ReminderRuleMapper reminderRuleMapper;
    @Autowired
    private HealthRecordMapper healthRecordMapper;

    @Override
    public NotificationVO create(Long userId, CreateNotificationDTO dto) {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(userId);
        entity.setTitle(dto.title());
        entity.setContent(dto.content());
        entity.setType(dto.type() == null || dto.type().isBlank() ? "system" : dto.type());
        entity.setIsRead(0);
        entity.setNotifyTime(dto.notifyTime() == null ? LocalDateTime.now() : dto.notifyTime());
        notificationMapper.insert(entity);
        return new NotificationVO(entity.getId(), entity.getTitle(), entity.getContent(), entity.getType(), entity.getIsRead(), entity.getNotifyTime());
    }

    @Override
    public List<NotificationVO> list(Long userId, Integer isRead) {
        return notificationMapper.selectByUserId(userId, isRead).stream()
                .map(n -> new NotificationVO(n.getId(), n.getTitle(), n.getContent(), n.getType(), n.getIsRead(), n.getNotifyTime()))
                .toList();
    }

    @Override
    public List<NotificationVO> listUnreadAfterId(Long userId, Long lastId, Integer limit) {
        long minId = lastId == null ? 0L : Math.max(lastId, 0L);
        int safeLimit = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        return notificationMapper.selectUnreadAfterId(userId, minId, safeLimit).stream()
                .map(n -> new NotificationVO(n.getId(), n.getTitle(), n.getContent(), n.getType(), n.getIsRead(), n.getNotifyTime()))
                .toList();
    }

    @Override
    public void read(Long userId, Long id) {
        notificationMapper.readById(id, userId);
    }

    @Override
    public ReminderRuleVO createRule(Long userId, CreateReminderRuleDTO dto) {
        ReminderRuleEntity entity = new ReminderRuleEntity();
        entity.setUserId(userId);
        entity.setTitle(dto.title());
        entity.setContent(dto.content());
        entity.setType(dto.type() == null || dto.type().isBlank() ? "medication" : dto.type());
        entity.setIntervalMinutes(dto.intervalMinutes());
        entity.setEnabled(1);
        entity.setNextTriggerTime(LocalDateTime.now().plusMinutes(dto.intervalMinutes()));
        reminderRuleMapper.insert(entity);
        return toRuleVO(entity);
    }

    @Override
    public List<ReminderRuleVO> listRules(Long userId) {
        return reminderRuleMapper.selectByUserId(userId).stream().map(this::toRuleVO).toList();
    }

    @Override
    public void executeDueRules() {
        LocalDateTime now = LocalDateTime.now();
        List<ReminderRuleEntity> dueRules = reminderRuleMapper.selectDueRules(now);
        for (ReminderRuleEntity rule : dueRules) {
            NotificationEntity notify = new NotificationEntity();
            notify.setUserId(rule.getUserId());
            notify.setTitle(rule.getTitle());
            notify.setContent(rule.getContent());
            notify.setType(rule.getType());
            notify.setIsRead(0);
            notify.setNotifyTime(now);
            notificationMapper.insert(notify);
            reminderRuleMapper.updateNextTriggerTime(rule.getId(), now.plusMinutes(rule.getIntervalMinutes()));
        }
    }

    @Override
    public void executeAbnormalMetricAlerts() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(10);
        List<HealthRecordEntity> recentRecords = healthRecordMapper.selectRecentRecords(start);
        for (HealthRecordEntity record : recentRecords) {
            if (!isAbnormal(record.getMetricType(), record.getMetricValue())) {
                continue;
            }
            String title = "异常指标提醒";
            String content = buildAbnormalContent(record);
            int exists = notificationMapper.countByUserTypeAndContent(record.getUserId(), AUTO_ALERT_TYPE, content);
            if (exists > 0) {
                continue;
            }
            NotificationEntity notify = new NotificationEntity();
            notify.setUserId(record.getUserId());
            notify.setTitle(title);
            notify.setContent(content);
            notify.setType(AUTO_ALERT_TYPE);
            notify.setIsRead(0);
            notify.setNotifyTime(LocalDateTime.now());
            notificationMapper.insert(notify);
        }
    }

    private ReminderRuleVO toRuleVO(ReminderRuleEntity r) {
        return new ReminderRuleVO(
                r.getId(),
                r.getTitle(),
                r.getContent(),
                r.getType(),
                r.getIntervalMinutes(),
                r.getEnabled(),
                r.getNextTriggerTime()
        );
    }

    private String buildAbnormalContent(HealthRecordEntity record) {
        String metricName = switch (record.getMetricType()) {
            case "blood_pressure" -> "血压";
            case "heart_rate" -> "心率";
            case "blood_glucose" -> "血糖";
            case "cholesterol" -> "胆固醇";
            default -> "健康指标";
        };
        return metricName + "出现异常波动：" + record.getMetricValue() + " " + (record.getUnit() == null ? "" : record.getUnit())
                + "，请及时关注（记录ID:" + record.getId() + "）";
    }

    private boolean isAbnormal(String metricType, String metricValue) {
        try {
            if ("blood_pressure".equals(metricType)) {
                String[] arr = metricValue == null ? new String[0] : metricValue.split("/");
                if (arr.length != 2) {
                    return false;
                }
                int high = Integer.parseInt(arr[0].trim());
                int low = Integer.parseInt(arr[1].trim());
                return high >= 140 || low >= 90 || high < 90 || low < 60;
            }
            double v = Double.parseDouble(metricValue == null ? "" : metricValue.trim());
            return switch (metricType) {
                case "blood_glucose" -> v > 7.0 || v < 3.9;
                case "heart_rate" -> v > 100 || v < 60;
                case "cholesterol" -> v > 5.2;
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }
}
