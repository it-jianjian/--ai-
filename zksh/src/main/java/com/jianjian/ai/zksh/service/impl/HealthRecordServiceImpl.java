package com.jianjian.ai.zksh.service.impl;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.domain.dto.CreateHealthRecordDTO;
import com.jianjian.ai.zksh.domain.dto.QueryHealthRecordDTO;
import com.jianjian.ai.zksh.domain.dto.UpdateHealthRecordDTO;
import com.jianjian.ai.zksh.domain.entity.HealthRecordEntity;
import com.jianjian.ai.zksh.domain.vo.HealthMetricOptionVO;
import com.jianjian.ai.zksh.domain.vo.HealthRecordTrendVO;
import com.jianjian.ai.zksh.domain.vo.HealthRecordVO;
import com.jianjian.ai.zksh.domain.vo.HealthSummaryVO;
import com.jianjian.ai.zksh.mapper.HealthRecordMapper;
import com.jianjian.ai.zksh.service.HealthRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class HealthRecordServiceImpl implements HealthRecordService {
    private static final List<HealthMetricOptionVO> METRIC_OPTIONS = List.of(
            new HealthMetricOptionVO("blood_pressure", "血压", "vital", List.of("mmHg")),
            new HealthMetricOptionVO("heart_rate", "心率", "vital", List.of("bpm")),
            new HealthMetricOptionVO("blood_glucose", "血糖", "lab", List.of("mmol/L")),
            new HealthMetricOptionVO("cholesterol", "胆固醇", "lab", List.of("mmol/L")),
            new HealthMetricOptionVO("weight", "体重", "lifestyle", List.of("kg")),
            new HealthMetricOptionVO("sleep_duration", "睡眠时长", "lifestyle", List.of("h")),
            new HealthMetricOptionVO("steps", "步数", "lifestyle", List.of("steps"))
    );
    private static final Map<String, String> TYPE_NAME_MAP = Map.of(
            "blood_pressure", "血压",
            "heart_rate", "心率",
            "blood_glucose", "血糖",
            "cholesterol", "胆固醇",
            "weight", "体重",
            "sleep_duration", "睡眠时长",
            "steps", "步数"
    );

    @Autowired
    private HealthRecordMapper healthRecordMapper;

    @Override
    public HealthRecordVO create(Long userId, CreateHealthRecordDTO dto) {
        HealthRecordEntity entity = new HealthRecordEntity();
        entity.setUserId(userId);
        entity.setMetricType(dto.metricType());
        entity.setMetricValue(dto.metricValue());
        entity.setUnit(dto.unit());
        entity.setRecordTime(dto.recordTime());
        entity.setRemark(dto.remark());
        healthRecordMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public List<HealthRecordVO> list(Long userId, QueryHealthRecordDTO dto) {
        return healthRecordMapper.selectByCondition(
                        userId,
                        dto.metricType(),
                        dto.startTime(),
                        dto.endTime()
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public List<HealthRecordTrendVO> trend(Long userId, QueryHealthRecordDTO dto) {
        if (dto.metricType() == null || dto.metricType().isBlank()) {
            throw new BizException("趋势查询必须传 metricType");
        }
        return healthRecordMapper.selectTrend(
                        userId,
                        dto.metricType(),
                        dto.startTime(),
                        dto.endTime()
                ).stream()
                .map(record -> new HealthRecordTrendVO(
                        record.getMetricType(),
                        record.getMetricValue(),
                        record.getRecordTime(),
                        calcRiskLevel(record.getMetricType(), record.getMetricValue())
                ))
                .toList();
    }

    @Override
    public HealthRecordVO update(Long userId, UpdateHealthRecordDTO dto) {
        HealthRecordEntity old = healthRecordMapper.selectById(dto.id(), userId);
        if (old == null) {
            throw new BizException("健康记录不存在");
        }
        HealthRecordEntity update = new HealthRecordEntity();
        update.setId(dto.id());
        update.setUserId(userId);
        update.setMetricType(dto.metricType());
        update.setMetricValue(dto.metricValue());
        update.setUnit(dto.unit());
        update.setRecordTime(dto.recordTime());
        update.setRemark(dto.remark());
        healthRecordMapper.updateById(update);
        return toVO(healthRecordMapper.selectById(dto.id(), userId));
    }

    @Override
    public void delete(Long userId, Long id) {
        if (healthRecordMapper.deleteById(id, userId) == 0) {
            throw new BizException("健康记录不存在");
        }
    }

    @Override
    public HealthSummaryVO summary(Long userId) {
        long total = healthRecordMapper.countByUserId(userId);
        long abnormal = healthRecordMapper.selectByCondition(userId, null, null, null).stream()
                .filter(x -> "HIGH".equals(calcRiskLevel(x.getMetricType(), x.getMetricValue())))
                .count();
        HealthRecordEntity latest = healthRecordMapper.selectLatestByUserId(userId);
        if (latest == null) {
            return new HealthSummaryVO(total, abnormal, null, null, null);
        }
        return new HealthSummaryVO(
                total,
                abnormal,
                TYPE_NAME_MAP.getOrDefault(latest.getMetricType(), latest.getMetricType()),
                latest.getMetricValue(),
                latest.getRecordTime() == null ? null : latest.getRecordTime().toString()
        );
    }

    @Override
    public List<HealthMetricOptionVO> options() {
        return METRIC_OPTIONS;
    }

    private HealthRecordVO toVO(HealthRecordEntity entity) {
        return new HealthRecordVO(
                entity.getId(),
                entity.getMetricType(),
                entity.getMetricValue(),
                entity.getUnit(),
                entity.getRecordTime(),
                entity.getRemark(),
                calcRiskLevel(entity.getMetricType(), entity.getMetricValue())
        );
    }

    private String calcRiskLevel(String metricType, String metricValue) {
        try {
            if ("blood_pressure".equals(metricType)) {
                String[] arr = metricValue.split("/");
                if (arr.length == 2) {
                    int high = Integer.parseInt(arr[0].trim());
                    int low = Integer.parseInt(arr[1].trim());
                    if (high >= 140 || low >= 90) return "HIGH";
                    if (high < 90 || low < 60) return "LOW";
                }
                return "NORMAL";
            }
            double v = Double.parseDouble(metricValue.trim());
            return switch (metricType) {
                case "blood_glucose" -> v > 7.0 ? "HIGH" : (v < 3.9 ? "LOW" : "NORMAL");
                case "heart_rate" -> v > 100 ? "HIGH" : (v < 60 ? "LOW" : "NORMAL");
                case "cholesterol" -> v > 5.2 ? "HIGH" : "NORMAL";
                case "weight" -> v > 100 ? "HIGH" : "NORMAL";
                default -> "NORMAL";
            };
        } catch (Exception e) {
            return "NORMAL";
        }
    }
}
