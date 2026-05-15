package com.jianjian.ai.zksh.controller;

import com.jianjian.ai.zksh.common.ApiResponse;
import com.jianjian.ai.zksh.domain.vo.HealthMetricOptionVO;
import com.jianjian.ai.zksh.service.HealthRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/health")
public class HealthMetaController {

    @Autowired
    private HealthRecordService healthRecordService;

    @GetMapping("/options")
    public ApiResponse<List<HealthMetricOptionVO>> options() {
        return ApiResponse.ok(healthRecordService.options());
    }
}
