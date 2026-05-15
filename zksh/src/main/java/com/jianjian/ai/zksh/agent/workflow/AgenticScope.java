package com.jianjian.ai.zksh.agent.workflow;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次会话请求范围内的共享上下文。
 */
@Data
public class AgenticScope {

    private Long userId;
    private Long sessionId;
    private String memoryId;
    private String originalQuery;

    private String rewrittenQuery;
    private String queryForAnswer;
    private String finalAnswer;
    private String riskLevel;

    private Map<String, String> stepErrors = new LinkedHashMap<>();
    private Map<String, Long> stepLatencyMs = new LinkedHashMap<>();

    public AgenticScope(Long userId, Long sessionId, String memoryId, String originalQuery) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.memoryId = memoryId;
        this.originalQuery = originalQuery;
        this.queryForAnswer = originalQuery;
    }
}
