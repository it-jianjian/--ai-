package com.jianjian.ai.zksh.service;

import com.jianjian.ai.zksh.config.AiSessionTopicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SessionTopicService {
    private static final Logger log = LoggerFactory.getLogger(SessionTopicService.class);

    private final SessionTopicAiService sessionTopicAiService;
    private final AiSessionTopicProperties properties;

    public SessionTopicService(SessionTopicAiService sessionTopicAiService, AiSessionTopicProperties properties) {
        this.sessionTopicAiService = sessionTopicAiService;
        this.properties = properties;
    }

    /**
     * 返回一个适合作为会话标题的主题短语；失败返回 null（由调用方决定回退策略）。
     */
    public String extractTopicOrNull(String question) {
        if (!properties.isLlmEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String q = normalize(question);
        if (!StringUtils.hasText(q)) {
            return null;
        }

        long timeoutMs = Math.max(200, properties.getTimeoutMs());
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> callModel(q));
            String raw = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
            String cleaned = cleanupTopic(raw);
            if (!StringUtils.hasText(cleaned)) {
                return null;
            }
            return enforceMaxLength(cleaned, properties.getMaxLength());
        } catch (Exception e) {
            log.debug("LLM 会话主题提炼失败，降级为规则回退。", e);
            return null;
        }
    }

    private String callModel(String question) {
        return sessionTopicAiService.topic(question);
    }

    private String cleanupTopic(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw
                .replaceAll("[\\r\\n]+", " ")
                .trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        // 常见“标题：xxx”格式处理
        s = s.replaceAll("^(标题|主题)[:：]\\s*", "").trim();
        // 去掉常见包裹符号
        s = s.replaceAll("^[\"'“”‘’《》]+", "").replaceAll("[\"'“”‘’《》]+$", "").trim();
        // 去掉明显的句末标点
        s = s.replaceAll("[，。！？；,.!?;:：]+$", "").trim();
        // 过于泛化的输出直接判空
        if ("新会话".equals(s) || "健康咨询".equals(s) || "咨询".equals(s)) {
            return null;
        }
        return s;
    }

    private String normalize(String question) {
        return question
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String enforceMaxLength(String topic, int maxLen) {
        int max = Math.max(8, maxLen);
        String s = topic.trim();
        return s.length() <= max ? s : s.substring(0, max).trim();
    }
}

