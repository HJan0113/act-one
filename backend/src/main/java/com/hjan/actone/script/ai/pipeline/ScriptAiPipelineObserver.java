package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.service.ScriptYamlValidationResult;

public interface ScriptAiPipelineObserver {

    ScriptAiPipelineObserver NOOP = new ScriptAiPipelineObserver() {
    };

    default void onPipelineStart(PipelineContext context) {
    }

    default void onStageStart(PipelineContext context, PipelineStage stage) {
    }

    default void onStageSuccess(PipelineContext context, PipelineStage stage) {
    }

    default void onStageResult(PipelineContext context, PipelineStage stage, Object result) {
    }

    default void onStageFailure(PipelineContext context, PipelineStage stage, RuntimeException error) {
    }

    default void onValidation(PipelineContext context, ScriptYamlValidationResult validationResult) {
    }

    default void onFallback(PipelineContext context, PipelineStage fallbackStage, String reason) {
    }

    default void onComplete(ScriptAiPipelineResult result) {
    }

    default boolean isCanceled() {
        return false;
    }
}
