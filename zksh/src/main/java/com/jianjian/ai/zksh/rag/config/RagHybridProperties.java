package com.jianjian.ai.zksh.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.hybrid")
public class RagHybridProperties {
    private Boolean enabled = Boolean.TRUE;
    private Integer vectorTopN = 30;
    private Integer keywordTopN = 30;
    private Integer mergeLimit = 50;
    private Integer finalTopK = 6;
    private Double vectorScoreThreshold = 0.3d;
    private Integer rrfK = 60;
}
