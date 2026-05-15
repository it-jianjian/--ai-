package com.jianjian.ai.zksh.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XxlJobConfig {

    @Bean
    @ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAccessToken(properties.getAccessToken());
        executor.setAppname(properties.getExecutor().getAppname());
        executor.setAddress(null);
        executor.setIp(properties.getExecutor().getIp());
        executor.setPort(properties.getExecutor().getPort());
        executor.setLogPath(properties.getExecutor().getLogpath());
        executor.setLogRetentionDays(properties.getExecutor().getLogretentiondays());
        return executor;
    }
}
