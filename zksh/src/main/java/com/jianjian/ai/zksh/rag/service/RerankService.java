package com.jianjian.ai.zksh.rag.service;

import com.jianjian.ai.zksh.rag.client.OpenAiCompatibleRerankClient;
import com.jianjian.ai.zksh.rag.config.RagRerankProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 候选精排服务：基于 rerank 模型对召回候选做二次排序。
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final OpenAiCompatibleRerankClient rerankClient;
    private final RagRerankProperties rerankProperties;

    public RerankService(OpenAiCompatibleRerankClient rerankClient, RagRerankProperties rerankProperties) {
        this.rerankClient = rerankClient;
        this.rerankProperties = rerankProperties;
    }

    public List<OpenAiCompatibleRerankClient.RerankItem> rerank(String query, List<String> candidates) {
        if (!isConfigured()) {
            log.info("RERANK未启用：配置不完整 baseUrl/apiKey/modelName 为空，直接跳过。");
            return List.of();
        }
        if (query == null || query.isBlank() || candidates == null || candidates.isEmpty()) {
            log.info("RERANK跳过：query为空或候选为空。");
            return List.of();
        }
        Integer topN = rerankProperties.getTopN();
        int effectiveTopN = (topN == null || topN <= 0) ? candidates.size() : Math.min(topN, candidates.size());
        long begin = System.currentTimeMillis();
        log.info("RERANK请求：provider={} model={} candidates={} topN={}",
                rerankProperties.getProvider(),
                rerankProperties.getModelName(),
                candidates.size(),
                effectiveTopN);
        return rerankClient.rerank(query, candidates, topN)
                .doOnNext(items -> {
                    long cost = System.currentTimeMillis() - begin;
                    Double top1 = (items == null || items.isEmpty()) ? null : items.get(0).score();
                    log.info("RERANK成功：items={} top1Score={} costMs={}",
                            items == null ? 0 : items.size(),
                            top1,
                            cost);
                })
                .doOnError(e -> {
                    long cost = System.currentTimeMillis() - begin;
                    log.warn("RERANK失败：costMs={} err={}", cost, e == null ? "" : e.getMessage());
                })
                .onErrorReturn(List.of())
                .block();
    }

    private boolean isConfigured() {
        return hasText(rerankProperties.getBaseUrl())
                && hasText(rerankProperties.getApiKey())
                && hasText(rerankProperties.getModelName());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

