package com.jianjian.ai.zksh.report.controller;

import com.jianjian.ai.zksh.common.ApiResponse;
import com.jianjian.ai.zksh.report.domain.dto.CreateReportTaskResponse;
import com.jianjian.ai.zksh.report.domain.dto.ReportTaskStatusResponse;
import com.jianjian.ai.zksh.report.domain.entity.ReportInterpretationEntity;
import com.jianjian.ai.zksh.report.domain.entity.ReportTaskEntity;
import com.jianjian.ai.zksh.report.domain.vo.ReportTaskListItemVO;
import com.jianjian.ai.zksh.report.domain.vo.ReportTaskStepLogVO;
import com.jianjian.ai.zksh.report.mapper.ReportInterpretationMapper;
import com.jianjian.ai.zksh.report.mapper.ReportTaskStepLogMapper;
import com.jianjian.ai.zksh.report.service.ReportTaskService;
import com.jianjian.ai.zksh.security.UserContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/report")
public class ReportTaskController {

    private final ReportTaskService reportTaskService;
    private final ReportInterpretationMapper reportInterpretationMapper;
    private final ReportTaskStepLogMapper reportTaskStepLogMapper;

    public ReportTaskController(ReportTaskService reportTaskService,
                                ReportInterpretationMapper reportInterpretationMapper,
                                ReportTaskStepLogMapper reportTaskStepLogMapper) {
        this.reportTaskService = reportTaskService;
        this.reportInterpretationMapper = reportInterpretationMapper;
        this.reportTaskStepLogMapper = reportTaskStepLogMapper;
    }

    @PostMapping("/tasks")
    public ApiResponse<CreateReportTaskResponse> createTask(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        ReportTaskEntity task = reportTaskService.createTask(userId, file);
        return ApiResponse.ok(new CreateReportTaskResponse(task.getTaskId(), task.getStatus(), task.getStage()));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<ReportTaskStatusResponse> getTask(@PathVariable String taskId) {
        Long userId = UserContext.getUserId();
        ReportTaskEntity task = reportTaskService.getTaskOrThrow(userId, taskId);
        ReportInterpretationEntity interp = reportInterpretationMapper.selectByTaskId(taskId);
        String summary = interp == null ? null : interp.getSummary();
        List<ReportTaskStepLogVO> stepLogs = reportTaskStepLogMapper.selectByTaskId(taskId);
        return ApiResponse.ok(new ReportTaskStatusResponse(task.getTaskId(), task.getStatus(), task.getStage(), task.getErrorMessage(), summary, stepLogs));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ReportTaskListItemVO>> listTasks(@RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserContext.getUserId();
        return ApiResponse.ok(reportTaskService.listRecent(userId, limit));
    }
}

