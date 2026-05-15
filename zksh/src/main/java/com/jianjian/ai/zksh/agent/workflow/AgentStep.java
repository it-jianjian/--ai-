package com.jianjian.ai.zksh.agent.workflow;

public interface AgentStep {

    WorkflowStepType type();

    StepResult execute(AgenticScope scope);
}
