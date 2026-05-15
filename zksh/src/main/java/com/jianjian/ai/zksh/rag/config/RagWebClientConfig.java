package com.jianjian.ai.zksh.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * RAG 相关的 HTTP 客户端构造器。
 *
 * <p>将 WebClient.Builder 作为 Bean 暴露，供 Qdrant/Embedding/Rerank 客户端统一复用。</p>
 */
@Configuration
public class RagWebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

