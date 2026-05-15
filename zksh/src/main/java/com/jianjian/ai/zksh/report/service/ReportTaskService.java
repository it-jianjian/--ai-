package com.jianjian.ai.zksh.report.service;

import com.jianjian.ai.zksh.common.BizException;
import com.jianjian.ai.zksh.report.domain.ReportTaskStage;
import com.jianjian.ai.zksh.report.domain.ReportTaskStatus;
import com.jianjian.ai.zksh.report.domain.entity.ReportTaskEntity;
import com.jianjian.ai.zksh.report.domain.vo.ReportTaskListItemVO;
import com.jianjian.ai.zksh.report.mapper.ReportTaskMapper;
import com.jianjian.ai.zksh.report.mq.ReportTaskCreatedEvent;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.List;

@Service
public class ReportTaskService {

    private final LocalReportFileStorage storage;
    private final ReportTaskMapper reportTaskMapper;
    private final AmqpTemplate amqpTemplate;
    private final com.jianjian.ai.zksh.report.config.ReportProperties props;

    public ReportTaskService(LocalReportFileStorage storage,
                             ReportTaskMapper reportTaskMapper,
                             AmqpTemplate amqpTemplate,
                             com.jianjian.ai.zksh.report.config.ReportProperties props) {
        this.storage = storage;
        this.reportTaskMapper = reportTaskMapper;
        this.amqpTemplate = amqpTemplate;
        this.props = props;
    }

    @Transactional
    public ReportTaskEntity createTask(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new BizException("未登录");
        }
        String taskId = "rpt_" + UUID.randomUUID().toString().replace("-", "");
        String filePath = storage.save(userId, taskId, file);

        ReportTaskEntity entity = new ReportTaskEntity();
        entity.setTaskId(taskId);
        entity.setUserId(userId);
        entity.setFileName(file.getOriginalFilename());
        entity.setFilePath(filePath);
        entity.setStatus(ReportTaskStatus.QUEUED.name());
        entity.setStage(ReportTaskStage.UPLOADED.name());
        entity.setErrorMessage(null);
        entity.setRetryCount(0);
        reportTaskMapper.insert(entity);

        ReportTaskCreatedEvent event = new ReportTaskCreatedEvent(taskId, userId);
        amqpTemplate.convertAndSend(props.getMq().getExchange(), props.getMq().getRoutingKey(), event);
        return entity;
    }

    public ReportTaskEntity getTaskOrThrow(Long userId, String taskId) {
        ReportTaskEntity task = reportTaskMapper.selectByTaskId(taskId, userId);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        return task;
    }

    public List<ReportTaskListItemVO> listRecent(Long userId, Integer limit) {
        if (userId == null) {
            throw new BizException("未登录");
        }
        int realLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        return reportTaskMapper.selectRecentByUserId(userId, realLimit);
    }
}

