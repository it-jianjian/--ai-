package com.jianjian.ai.zksh.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Query Rewriting 配置（application.yml -> rag.rewrite.*）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.rewrite")
public class RagRewriteProperties {
    /**
     * 是否启用 LLM Query Rewriting。
     */
    private Boolean enabled = Boolean.TRUE;

    /**
     * LLM 改写超时（毫秒）。
     */
    private Integer timeoutMs = 1200;

    /**
     * 改写 query 最大长度（字符数）。
     */
    private Integer maxLength = 120;
}

