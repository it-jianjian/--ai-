package com.jianjian.ai.zksh.service;

import com.jianjian.ai.zksh.domain.dto.CreateHealthRecordDTO;
import com.jianjian.ai.zksh.domain.dto.QueryHealthRecordDTO;
import com.jianjian.ai.zksh.domain.dto.UpdateHealthRecordDTO;
import com.jianjian.ai.zksh.domain.vo.HealthMetricOptionVO;
import com.jianjian.ai.zksh.domain.vo.HealthRecordTrendVO;
import com.jianjian.ai.zksh.domain.vo.HealthRecordVO;
import com.jianjian.ai.zksh.domain.vo.HealthSummaryVO;

import java.util.List;

public interface HealthRecordService {
    HealthRecordVO create(Long userId, CreateHealthRecordDTO dto);

    List<HealthRecordVO> list(Long userId, QueryHealthRecordDTO dto);

    List<HealthRecordTrendVO> trend(Long userId, QueryHealthRecordDTO dto);

    HealthRecordVO update(Long userId, UpdateHealthRecordDTO dto);

    void delete(Long userId, Long id);

    HealthSummaryVO summary(Long userId);

    List<HealthMetricOptionVO> options();
}
