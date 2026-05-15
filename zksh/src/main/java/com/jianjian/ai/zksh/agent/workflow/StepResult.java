package com.jianjian.ai.zksh.agent.workflow;

public record StepResult(boolean success, String errorMessage) {

    public static StepResult ok() {
        return new StepResult(true, null);
    }

    public static StepResult fail(String errorMessage) {
        return new StepResult(false, errorMessage);
    }
}
