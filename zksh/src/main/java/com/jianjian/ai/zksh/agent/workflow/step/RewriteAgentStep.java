package com.jianjian.ai.zksh.agent.workflow.step;

import com.jianjian.ai.zksh.agent.workflow.AgentStep;
import com.jianjian.ai.zksh.agent.workflow.AgenticScope;
import com.jianjian.ai.zksh.agent.workflow.StepResult;
import com.jianjian.ai.zksh.agent.workflow.WorkflowStepType;
import com.jianjian.ai.zksh.rag.service.QueryRewriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Order(10)
public class RewriteAgentStep implements AgentStep {

    @Autowired
    private QueryRewriteService queryRewriteService;

    @Override
    public WorkflowStepType type() {
        return WorkflowStepType.REWRITE;
    }

    @Override
    public StepResult execute(AgenticScope scope) {
        try {
            QueryRewriteService.RewriteResult result = queryRewriteService.rewriteOrNull(scope.getOriginalQuery());
            if (result == null || !StringUtils.hasText(result.rewrittenQuery())) {
                scope.setQueryForAnswer(scope.getOriginalQuery());
                return StepResult.ok();
            }
            scope.setRewrittenQuery(result.rewrittenQuery());
            scope.setQueryForAnswer(result.rewrittenQuery());
            return StepResult.ok();
        } catch (Exception e) {
            scope.setQueryForAnswer(scope.getOriginalQuery());
            return StepResult.fail("rewrite step error: " + e.getMessage());
        }
    }
}
