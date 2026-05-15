package com.jianjian.ai.zksh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.session-topic")
public class AiSessionTopicProperties {

    /**
     * 是否启用 LLM 会话主题提炼。
     */
    private boolean llmEnabled = true;

    /**
     * LLM 提炼超时（毫秒）。
     */
    private long timeoutMs = 1200;

    /**
     * 主题最大长度（字符数）。
     */
    private int maxLength = 24;

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }
}

