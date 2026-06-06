package com.hjan.actone.script.service;

import com.hjan.actone.common.web.SseEmitterSender;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineObserver;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineResult;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.model.ScriptYamlGenerateResponse;
import com.hjan.actone.script.model.ScriptYamlStreamEventType;
import com.hjan.actone.script.model.ScriptYamlStreamPayload;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ScriptYamlStreamService {

    private static final long SSE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    private static final Map<PipelineStage, Integer> STAGE_START_PROGRESS = new EnumMap<>(PipelineStage.class);
    private static final Map<PipelineStage, Integer> STAGE_SUCCESS_PROGRESS = new EnumMap<>(PipelineStage.class);

    static {
        register(PipelineStage.INIT, 2, 8);
        register(PipelineStage.CHAPTER_SUMMARY, 10, 20);
        register(PipelineStage.GLOBAL_STORY_EXTRACTION, 22, 32);
        register(PipelineStage.SCENE_PLANNING, 34, 44);
        register(PipelineStage.PSYCHOLOGICAL_VISUALIZATION, 46, 56);
        register(PipelineStage.DIALOGUE_GENERATION, 58, 68);
        register(PipelineStage.SOURCE_TRACE, 70, 78);
        register(PipelineStage.SCRIPT_YAML_GENERATION, 80, 86);
        register(PipelineStage.YAML_VALIDATION, 88, 92);
        register(PipelineStage.YAML_REPAIR, 93, 96);
        register(PipelineStage.COMPLETED, 100, 100);
    }

    private final ScriptYamlGenerationService generationService;

    public ScriptYamlStreamService(ScriptYamlGenerationService generationService) {
        this.generationService = generationService;
    }

    public SseEmitter streamGenerateFromText(ScriptYamlGenerateRequest request) {
        String taskId = "actone-" + UUID.randomUUID().toString().replace("-", "");
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        CompletableFuture.runAsync(() -> runGeneration(request, taskId, sender));
        return emitter;
    }

    private void runGeneration(ScriptYamlGenerateRequest request, String taskId, SseEmitterSender sender) {
        send(sender, ScriptYamlStreamEventType.META, ScriptYamlStreamPayload.of(
                taskId,
                PipelineStage.INIT.name(),
                progress(PipelineStage.INIT, true),
                "转换任务已创建"
        ));
        send(sender, ScriptYamlStreamEventType.MESSAGE, ScriptYamlStreamPayload.of(
                taskId,
                PipelineStage.INIT.name(),
                progress(PipelineStage.INIT, false),
                "正在准备小说章节解析与 AI Pipeline"
        ));

        try {
            ScriptYamlGenerateResponse result = generationService.generateFromText(
                    request,
                    new StreamObserver(taskId, sender)
            );
            send(sender, ScriptYamlStreamEventType.VALIDATION, ScriptYamlStreamPayload.validation(
                    taskId,
                    PipelineStage.YAML_VALIDATION.name(),
                    98,
                    result.validationStatus(),
                    result.validationError(),
                    "valid".equalsIgnoreCase(result.validationStatus())
            ));
            send(sender, ScriptYamlStreamEventType.FINISH, ScriptYamlStreamPayload.finish(taskId, result));
            send(sender, ScriptYamlStreamEventType.DONE, "[DONE]");
            sender.complete();
        } catch (RuntimeException error) {
            send(sender, ScriptYamlStreamEventType.ERROR, ScriptYamlStreamPayload.of(
                    taskId,
                    PipelineStage.INIT.name(),
                    100,
                    safeMessage(error)
            ));
            send(sender, ScriptYamlStreamEventType.DONE, "[DONE]");
            sender.complete();
        }
    }

    private static void register(PipelineStage stage, int startProgress, int successProgress) {
        STAGE_START_PROGRESS.put(stage, startProgress);
        STAGE_SUCCESS_PROGRESS.put(stage, successProgress);
    }

    private static int progress(PipelineStage stage, boolean start) {
        return (start ? STAGE_START_PROGRESS : STAGE_SUCCESS_PROGRESS).getOrDefault(stage, 0);
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "YAML 生成失败。" : message;
    }

    private static void send(SseEmitterSender sender, ScriptYamlStreamEventType eventType, Object payload) {
        sender.sendEvent(eventType.value(), payload);
    }

    private static final class StreamObserver implements ScriptAiPipelineObserver {

        private final String taskId;
        private final SseEmitterSender sender;

        private StreamObserver(String taskId, SseEmitterSender sender) {
            this.taskId = taskId;
            this.sender = sender;
        }

        @Override
        public void onPipelineStart(PipelineContext context) {
            sendProgress(PipelineStage.INIT, false, "章节解析完成，开始执行 AI Pipeline");
        }

        @Override
        public void onStageStart(PipelineContext context, PipelineStage stage) {
            sendProgress(stage, true, stage.name() + " 阶段开始");
        }

        @Override
        public void onStageSuccess(PipelineContext context, PipelineStage stage) {
            sendProgress(stage, false, stage.name() + " 阶段完成");
        }

        @Override
        public void onStageFailure(PipelineContext context, PipelineStage stage, RuntimeException error) {
            send(ScriptYamlStreamEventType.MESSAGE, ScriptYamlStreamPayload.of(
                    taskId,
                    stage.name(),
                    progress(stage, false),
                    stage.name() + " 阶段失败，准备进入显式降级"
            ));
        }

        @Override
        public void onValidation(PipelineContext context, ScriptYamlValidationResult validationResult) {
            ScriptYamlStreamService.send(sender, ScriptYamlStreamEventType.VALIDATION, ScriptYamlStreamPayload.validation(
                    taskId,
                    PipelineStage.YAML_VALIDATION.name(),
                    progress(PipelineStage.YAML_VALIDATION, false),
                    validationResult.status(),
                    validationResult.errorMessage(),
                    validationResult.valid()
            ));
        }

        @Override
        public void onFallback(PipelineContext context, PipelineStage fallbackStage, String reason) {
            send(ScriptYamlStreamEventType.MESSAGE, ScriptYamlStreamPayload.of(
                    taskId,
                    fallbackStage.name(),
                    progress(fallbackStage, false),
                    reason
            ));
        }

        @Override
        public void onComplete(ScriptAiPipelineResult result) {
            sendProgress(result.context().currentStage(), false, "AI Pipeline 执行完成");
        }

        private void sendProgress(PipelineStage stage, boolean start, String message) {
            send(ScriptYamlStreamEventType.PROGRESS, ScriptYamlStreamPayload.of(
                    taskId,
                    stage.name(),
                    progress(stage, start),
                    message
            ));
        }

        private void send(ScriptYamlStreamEventType eventType, Object payload) {
            ScriptYamlStreamService.send(sender, eventType, payload);
        }
    }
}
