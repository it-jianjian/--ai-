package com.jianjian.ai.zksh.report.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.report")
public class ReportProperties {
    private String storageDir;
    private Ocr ocr = new Ocr();
    private Mq mq = new Mq();
    private Pdf pdf = new Pdf();

    @Data
    public static class Ocr {
        private String baseUrl;
        private String path;
        private String appcode;
        private Integer timeoutMs = 15000;
    }

    @Data
    public static class Pdf {
        private Integer maxPages = 3;
        private Integer renderDpi = 180;
    }

    @Data
    public static class Mq {
        private String exchange;
        private String routingKey;
        private String queue;
    }
}

