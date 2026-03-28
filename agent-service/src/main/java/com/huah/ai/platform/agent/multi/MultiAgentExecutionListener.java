package com.huah.ai.platform.agent.multi;

public interface MultiAgentExecutionListener {

    default void onStageStarted(String stage, String label) {
    }

    default void onStageCompleted(MultiAgentExecutionStep step) {
    }

    default void onFailed(String stage, String errorMessage) {
    }
}
