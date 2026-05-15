package com.jianjian.ai.zksh.controller;

import com.jianjian.ai.zksh.common.ApiResponse;
import com.jianjian.ai.zksh.domain.dto.CreateHealthRecordDTO;
import com.jianjian.ai.zksh.domain.dto.QueryHealthRecordDTO;
import com.jianjian.ai.zksh.domain.dto.UpdateHealthRecordDTO;
import com.jianjian.ai.zksh.domain.vo.HealthMetricOptionVO;
import com.jianjian.ai.zksh.domain.vo.HealthRecordTrendVO;
import com.jianjian.ai.zksh.domain.vo.HealthRecordVO;
import com.jianjian.ai.zksh.domain.vo.HealthSummaryVO;
import com.jianjian.ai.zksh.security.UserContext;
import com.jianjian.ai.zksh.service.HealthRecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health/records")
public class HealthRecordController {

    @Autowired
    private HealthRecordService healthRecordService;

    @PostMapping
    public ApiResponse<HealthRecordVO> create(@Valid @RequestBody CreateHealthRecordDTO dto) {
        return ApiResponse.ok(healthRecordService.create(UserContext.getUserId(), dto));
    }

    @GetMapping
    public ApiResponse<List<HealthRecordVO>> list(QueryHealthRecordDTO dto) {
        return ApiResponse.ok(healthRecordService.list(UserContext.getUserId(), dto));
    }

    @GetMapping("/trend")
    public ApiResponse<List<HealthRecordTrendVO>> trend(QueryHealthRecordDTO dto) {
        return ApiResponse.ok(healthRecordService.trend(UserContext.getUserId(), dto));
    }

    @PutMapping
    public ApiResponse<HealthRecordVO> update(@Valid @RequestBody UpdateHealthRecordDTO dto) {
        return ApiResponse.ok(healthRecordService.update(UserContext.getUserId(), dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> delete(@PathVariable Long id) {
        healthRecordService.delete(UserContext.getUserId(), id);
        return ApiResponse.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/summary")
    public ApiResponse<HealthSummaryVO> summary() {
        return ApiResponse.ok(healthRecordService.summary(UserContext.getUserId()));
    }

    @GetMapping("/options")
    public ApiResponse<List<HealthMetricOptionVO>> options() {
        return ApiResponse.ok(healthRecordService.options());
    }
}
