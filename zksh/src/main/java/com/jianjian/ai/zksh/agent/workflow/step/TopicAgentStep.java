package com.jianjian.ai.zksh.agent.workflow.step;

import com.jianjian.ai.zksh.agent.workflow.AgentStep;
import com.jianjian.ai.zksh.agent.workflow.AgenticScope;
import com.jianjian.ai.zksh.agent.workflow.StepResult;
import com.jianjian.ai.zksh.agent.workflow.WorkflowStepType;
import com.jianjian.ai.zksh.service.AiConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(20)
public class TopicAgentStep implements AgentStep {

    @Autowired
    private AiConversationService aiConversationService;

    @Override
    public WorkflowStepType type() {
        return WorkflowStepType.TOPIC;
    }

    @Override
    public StepResult execute(AgenticScope scope) {
        try {
            aiConversationService.refineSessionTopicByQuestion(
                    scope.getUserId(),
                    scope.getSessionId(),
                    scope.getQueryForAnswer()
            );
            return StepResult.ok();
        } catch (Exception e) {
            return StepResult.fail("topic step error: " + e.getMessage());
        }
    }
}
