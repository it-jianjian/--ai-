package com.jianjian.ai.zksh.service;

import com.jianjian.ai.zksh.config.AiMemorySummaryProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class MemorySummaryService {

    private final MemorySummaryAiService aiService;
    private final AiMemorySummaryProperties properties;

    public MemorySummaryService(MemorySummaryAiService aiService, AiMemorySummaryProperties properties) {
        this.aiService = aiService;
        this.properties = properties;
    }

    public String summarize(String existingSummary, String historyText) {
        if (!properties.isEnabled()) {
            return existingSummary;
        }
        if (!StringUtils.hasText(historyText)) {
            return existingSummary;
        }
        String prompt = buildPrompt(existingSummary, historyText);
        long timeoutMs = Math.max(500, properties.getTimeoutMs());
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> aiService.summarize(prompt));
            String summary = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
            if (!StringUtils.hasText(summary)) {
                return existingSummary;
            }
            String normalized = normalize(summary);
            return StringUtils.hasText(normalized) ? normalized : existingSummary;
        } catch (Exception e) {
            return existingSummary;
        }
    }

    private String buildPrompt(String existingSummary, String historyText) {
        StringBuilder sb = new StringBuilder();
        sb.append("已有摘要：\n");
        sb.append(StringUtils.hasText(existingSummary) ? existingSummary : "（无）");
        sb.append("\n\n新增历史片段：\n");
        sb.append(historyText);
        sb.append("\n\n请输出更新后的统一摘要。");
        return sb.toString();
    }

    private String normalize(String text) {
        return text.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
