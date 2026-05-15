package com.jianjian.ai.zksh.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 配置（application.yml -> rag.embedding.*）。
 *
 * <p>V1 主要用于 OpenAI Compatible 的 embedding 接口，后续可按 provider 扩展不同服务商。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.embedding")
public class RagEmbeddingProperties {
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer timeoutMs;
}

