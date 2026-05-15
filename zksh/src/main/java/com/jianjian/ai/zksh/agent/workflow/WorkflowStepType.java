package com.jianjian.ai.zksh.agent.workflow;

public enum WorkflowStepType {
    REWRITE("rewrite"),
    TOPIC("topic"),
    ANSWER("answer");

    private final String code;

    WorkflowStepType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
