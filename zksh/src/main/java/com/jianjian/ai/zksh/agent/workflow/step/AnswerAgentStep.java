package com.jianjian.ai.zksh.agent.workflow.step;

import com.jianjian.ai.zksh.agent.workflow.AgentStep;
import com.jianjian.ai.zksh.agent.workflow.AgenticScope;
import com.jianjian.ai.zksh.agent.workflow.StepResult;
import com.jianjian.ai.zksh.agent.workflow.WorkflowStepType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 回答步骤当前负责兜底 query，真正的流式回答仍由 Controller 调用 ConsultantService 处理。
 */
@Service
@Order(30)
public class AnswerAgentStep implements AgentStep {

    @Override
    public WorkflowStepType type() {
        return WorkflowStepType.ANSWER;
    }

    @Override
    public StepResult execute(AgenticScope scope) {
        if (!StringUtils.hasText(scope.getQueryForAnswer())) {
            scope.setQueryForAnswer(scope.getOriginalQuery());
        }
        return StepResult.ok();
    }
}
