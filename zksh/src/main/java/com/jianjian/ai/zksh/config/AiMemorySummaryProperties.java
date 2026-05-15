package com.jianjian.ai.zksh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.memory-summary")
public class AiMemorySummaryProperties {

    /**
     * 是否启用超窗摘要记忆。
     */
    private boolean enabled = true;

    /**
     * 保留最近原始消息数（不含 system 和 summary）。
     */
    private int keepRecentMessages = 12;

    /**
     * 摘要超时（毫秒）。
     */
    private long timeoutMs = 1800;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getKeepRecentMessages() {
        return keepRecentMessages;
    }

    public void setKeepRecentMessages(int keepRecentMessages) {
        this.keepRecentMessages = keepRecentMessages;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
