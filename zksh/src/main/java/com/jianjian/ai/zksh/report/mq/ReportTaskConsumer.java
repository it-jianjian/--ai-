package com.jianjian.ai.zksh.report.mq;

import com.jianjian.ai.zksh.report.config.ReportProperties;
import com.jianjian.ai.zksh.report.service.ReportExtractionAndPersistService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReportTaskConsumer {

    private final ReportExtractionAndPersistService service;
    private final ReportProperties props;

    public ReportTaskConsumer(ReportExtractionAndPersistService service, ReportProperties props) {
        this.service = service;
        this.props = props;
    }

    @RabbitListener(queues = "#{reportProperties.mq.queue}")
    public void onCreated(ReportTaskCreatedEvent event) {
        if (event == null || event.taskId() == null) return;
        service.processTask(event.taskId());
    }
}

