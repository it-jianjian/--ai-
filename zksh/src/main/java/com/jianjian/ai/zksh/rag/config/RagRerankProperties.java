package com.jianjian.ai.zksh.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Rerank 配置（application.yml -> rag.rerank.*）。
 *
 * <p>本次先把配置结构落下来，后续在“检索链路（V1）”里接入 rerank 服务时直接复用。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.rerank")
public class RagRerankProperties {
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer topN;
    private Integer timeoutMs;
}

