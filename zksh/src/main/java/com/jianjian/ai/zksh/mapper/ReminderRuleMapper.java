package com.jianjian.ai.zksh.mapper;

import com.jianjian.ai.zksh.domain.entity.ReminderRuleEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderRuleMapper {
    int insert(ReminderRuleEntity entity);

    List<ReminderRuleEntity> selectByUserId(@Param("userId") Long userId);

    List<ReminderRuleEntity> selectDueRules(@Param("now") LocalDateTime now);

    int updateNextTriggerTime(@Param("id") Long id, @Param("nextTriggerTime") LocalDateTime nextTriggerTime);
}
