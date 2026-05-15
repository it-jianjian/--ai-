package com.jianjian.ai.zksh.report.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportMqConfig {

    @Bean
    public DirectExchange reportExchange(ReportProperties props) {
        return new DirectExchange(props.getMq().getExchange(), true, false);
    }

    @Bean
    public Queue reportTaskCreatedQueue(ReportProperties props) {
        return new Queue(props.getMq().getQueue(), true);
    }

    @Bean
    public Binding reportTaskCreatedBinding(DirectExchange reportExchange, Queue reportTaskCreatedQueue, ReportProperties props) {
        return BindingBuilder.bind(reportTaskCreatedQueue).to(reportExchange).with(props.getMq().getRoutingKey());
    }
}

