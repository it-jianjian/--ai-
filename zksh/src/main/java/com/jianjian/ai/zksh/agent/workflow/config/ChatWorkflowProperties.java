package com.jianjian.ai.zksh.agent.workflow.config;

import com.jianjian.ai.zksh.agent.workflow.WorkflowStepType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.workflow")
public class ChatWorkflowProperties {

    /**
     * 工作流总开关，关闭后保留原始 query 直接进入回答阶段。
     */
    private boolean enabled = true;

    /**
     * 步骤失败时是否继续执行后续步骤。
     */
    private boolean continueOnError = true;

    /**
     * 默认步骤超时时间（毫秒）。
     */
    private long defaultTimeoutMs = 1200;

    private Step rewrite = new Step();
    private Step topic = new Step();
    private Step answer = new Step();

    @Data
    public static class Step {
        private boolean enabled = true;
        private long timeoutMs = 1200;
        /**
         * 本步骤失败后是否仍执行后续；为 null 时沿用全局 {@link ChatWorkflowProperties#continueOnError}。
         */
        private Boolean continueOnError;
    }

    public Step resolveStep(WorkflowStepType stepType) {
        return switch (stepType) {
            case REWRITE -> rewrite;
            case TOPIC -> topic;
            case ANSWER -> answer;
        };
    }

    public boolean resolveContinueOnError(WorkflowStepType stepType) {
        Step s = resolveStep(stepType);
        Boolean per = s.getContinueOnError();
        return per != null ? per : continueOnError;
    }
}
