package com.jianjian.ai.zksh.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {
    private boolean enabled = false;
    private String adminAddresses;
    private String accessToken;
    private Executor executor = new Executor();

    @Getter
    @Setter
    public static class Executor {
        private String appname = "zksh-notify-executor";
        private String ip;
        private int port = 9999;
        private String logpath = "logs/xxl-job";
        private int logretentiondays = 30;
    }
}
