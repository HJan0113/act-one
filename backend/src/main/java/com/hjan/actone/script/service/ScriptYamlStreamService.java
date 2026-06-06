package com.hjan.actone.script.service;

import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.web.SseEmitterSender;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineCanceledException;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineObserver;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineResult;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineResumeState;
import com.hjan.actone.script.ai.pipeline.ScriptYamlFactConstraintChecker;
import com.hjan.actone.script.model.ScriptConversionTaskResponse;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.model.ScriptYamlGenerateResponse;
import com.hjan.actone.script.model.ScriptYamlStreamEventType;
import com.hjan.actone.script.model.ScriptYamlStreamPayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        register(PipelineStage.SOURCE_TRACE, 70, 76);
        register(PipelineStage.CONSISTENCY_CHECK, 78, 82);
        register(PipelineStage.SCRIPT_YAML_GENERATION, 84, 87);
        register(PipelineStage.YAML_VALIDATION, 88, 90);
        register(PipelineStage.FACT_CONSTRAINT_CHECK, 91, 93);
        register(PipelineStage.YAML_REPAIR, 94, 96);
        register(PipelineStage.COMPLETED, 100, 100);
    }

    private final Map<String, RunningTask> runningTasks = new ConcurrentHashMap<>();
    private final ScriptYamlGenerationService generationService;
    private final ScriptConversionTaskService conversionTaskService;
    private final ScriptStageResultService stageResultService;

    public ScriptYamlStreamService(ScriptYamlGenerationService generationService,
                                   ScriptConversionTaskService conversionTaskService,
                                   ScriptStageResultService stageResultService) {
        this.generationService = generationService;
        this.conversionTaskService = conversionTaskService;
        this.stageResultService = stageResultService;
    }

    public SseEmitter streamGenerateFromText(ScriptYamlGenerateRequest request) {
        LoginUser loginUser = UserContext.get();
        RunningTask runningTask = createRunningTask(request);
        runningTasks.put(runningTask.taskId(), runningTask);

        CompletableFuture.runAsync(() -> runGeneration(request, runningTask, loginUser));
        return runningTask.emitter();
    }

    public ScriptConversionTaskResponse cancel(String taskId) {
        RunningTask runningTask = runningTasks.get(taskId);
        PipelineStage stage = runningTask == null ? PipelineStage.INIT : runningTask.currentStage();
        ScriptConversionTaskResponse response = conversionTaskService.markCanceled(taskId, stage, "用户停止生成");
        if (runningTask != null && ScriptConversionTaskService.STATUS_CANCELED.equals(response.status())) {
            sendCancelAndDone(runningTask, stage, "生成已停止，后续阶段结果会被忽略。");
        }
        return response;
    }

    private RunningTask createRunningTask(ScriptYamlGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseEmitterSender sender = new SseEmitterSender(emitter);
        if (StringUtils.hasText(request.resumeTaskId())) {
            ScriptConversionTaskResponse task = conversionTaskService.retryFailed(request.resumeTaskId());
            if (StringUtils.hasText(request.projectId()) && !request.projectId().trim().equals(task.projectId())) {
                throw new IllegalArgumentException("恢复任务与当前项目不匹配。");
            }
            return new RunningTask(task.taskId(), emitter, sender, true);
        }
        if (StringUtils.hasText(request.projectId())) {
            ScriptConversionTaskResponse task = conversionTaskService.createOrReuse(
                    request.projectId(),
                    ScriptYamlDraftService.SCHEMA_VERSION,
                    null,
                    Boolean.TRUE.equals(request.forceNewTask())
            );
            return new RunningTask(task.taskId(), emitter, sender, true);
        }
        String taskId = "actone-" + UUID.randomUUID().toString().replace("-", "");
        return new RunningTask(taskId, emitter, sender, false);
    }

    private void runGeneration(ScriptYamlGenerateRequest request, RunningTask runningTask, LoginUser loginUser) {
        if (loginUser != null) {
            UserContext.set(loginUser);
        }
        try {
            runGenerationWithContext(request, runningTask);
        } finally {
            runningTasks.remove(runningTask.taskId());
            UserContext.clear();
        }
    }

    private void runGenerationWithContext(ScriptYamlGenerateRequest request, RunningTask runningTask) {
        send(runningTask.sender(), ScriptYamlStreamEventType.META, ScriptYamlStreamPayload.of(
                runningTask.taskId(),
                PipelineStage.INIT.name(),
                progress(PipelineStage.INIT, true),
                "转换任务已创建"
        ));
        markRunning(runningTask, PipelineStage.INIT, progress(PipelineStage.INIT, true), "转换任务已创建");

        if (runningTask.isCanceled()) {
            sendCancelAndDone(runningTask, PipelineStage.INIT, "生成已停止。");
            return;
        }

        send(runningTask.sender(), ScriptYamlStreamEventType.MESSAGE, ScriptYamlStreamPayload.of(
                runningTask.taskId(),
                PipelineStage.INIT.name(),
                progress(PipelineStage.INIT, false),
                "正在准备小说章节解析与 AI Pipeline"
        ));
        ScriptAiPipelineResumeState resumeState = loadResumeState(request, runningTask);

        try {
            ScriptYamlGenerateResponse result = generationService.generateFromText(
                    request,
                    new StreamObserver(runningTask),
                    resumeState
            );
            if (runningTask.isCanceled()) {
                sendCancelAndDone(runningTask, runningTask.currentStage(), "生成已停止。");
                return;
            }
            send(runningTask.sender(), ScriptYamlStreamEventType.VALIDATION, ScriptYamlStreamPayload.validation(
                    runningTask.taskId(),
                    PipelineStage.YAML_VALIDATION.name(),
                    98,
                    result.validationStatus(),
                    result.validationError(),
                    "valid".equalsIgnoreCase(result.validationStatus())
            ));
            markSuccess(runningTask, result);
            send(runningTask.sender(), ScriptYamlStreamEventType.FINISH, ScriptYamlStreamPayload.finish(runningTask.taskId(), result));
            send(runningTask.sender(), ScriptYamlStreamEventType.DONE, "[DONE]");
            runningTask.sender().complete();
        } catch (ScriptAiPipelineCanceledException error) {
            PipelineStage stage = error.stage() == null ? runningTask.currentStage() : error.stage();
            markCanceled(runningTask, stage, "用户停止生成");
            sendCancelAndDone(runningTask, stage, "生成已停止。");
        } catch (RuntimeException error) {
            PipelineStage stage = runningTask.currentStage();
            ScriptConversionTaskResponse failedTask = markFailed(runningTask, stage, error);
            send(runningTask.sender(), ScriptYamlStreamEventType.ERROR, ScriptYamlStreamPayload.failure(
                    runningTask.taskId(),
                    stage.name(),
                    100,
                    safeMessage(error),
                    failedTask
            ));
            send(runningTask.sender(), ScriptYamlStreamEventType.DONE, "[DONE]");
            runningTask.sender().complete();
        }
    }

    private void sendCancelAndDone(RunningTask runningTask, PipelineStage stage, String message) {
        if (!runningTask.cancel()) {
            return;
        }
        send(runningTask.sender(), ScriptYamlStreamEventType.CANCEL, ScriptYamlStreamPayload.canceled(
                runningTask.taskId(),
                stage.name(),
                Math.max(progress(stage, false), runningTask.progress()),
                message
        ));
        send(runningTask.sender(), ScriptYamlStreamEventType.DONE, "[DONE]");
        runningTask.sender().complete();
    }

    private void markRunning(RunningTask runningTask, PipelineStage stage, int progress, String message) {
        runningTask.update(stage, progress);
        if (runningTask.persistent()) {
            conversionTaskService.markRunning(runningTask.taskId(), stage, progress, message);
        }
    }

    private void markSuccess(RunningTask runningTask, ScriptYamlGenerateResponse result) {
        runningTask.update(PipelineStage.COMPLETED, 100);
        if (runningTask.persistent()) {
            String message = "valid".equalsIgnoreCase(result.validationStatus()) ? "转换完成，Schema 校验通过" : "转换完成，需检查 Schema";
            conversionTaskService.markSuccess(runningTask.taskId(), null, message);
        }
    }

    private ScriptConversionTaskResponse markFailed(RunningTask runningTask, PipelineStage stage, RuntimeException error) {
        if (runningTask.persistent()) {
            return conversionTaskService.markFailed(runningTask.taskId(), stage, error);
        }
        return null;
    }

    private void markCanceled(RunningTask runningTask, PipelineStage stage, String message) {
        runningTask.cancelSilently();
        if (runningTask.persistent()) {
            conversionTaskService.markCanceled(runningTask.taskId(), stage, message);
        }
    }

    private ScriptAiPipelineResumeState loadResumeState(ScriptYamlGenerateRequest request, RunningTask runningTask) {
        if (!runningTask.persistent() || !StringUtils.hasText(request.resumeTaskId())) {
            return ScriptAiPipelineResumeState.empty();
        }

        Optional<List<ChapterSummary>> chapterSummaries = stageResultService.loadChapterSummaries(runningTask.taskId());
        if (chapterSummaries.isEmpty()) {
            send(runningTask.sender(), ScriptYamlStreamEventType.MESSAGE, ScriptYamlStreamPayload.of(
                    runningTask.taskId(),
                    PipelineStage.CHAPTER_SUMMARY.name(),
                    progress(PipelineStage.CHAPTER_SUMMARY, true),
                    "未找到可复用阶段结果，将从章节摘要重新开始"
            ));
            return ScriptAiPipelineResumeState.empty();
        }

        Optional<GlobalStoryState> globalStoryState = stageResultService.loadGlobalStoryState(runningTask.taskId());
        Optional<ScenePlan> scenePlan = globalStoryState.isPresent()
                ? stageResultService.loadScenePlan(runningTask.taskId())
                : Optional.empty();
        PipelineStage resumeStage = scenePlan.isPresent()
                ? PipelineStage.PSYCHOLOGICAL_VISUALIZATION
                : globalStoryState.isPresent()
                ? PipelineStage.SCENE_PLANNING
                : PipelineStage.GLOBAL_STORY_EXTRACTION;
        send(runningTask.sender(), ScriptYamlStreamEventType.MESSAGE, ScriptYamlStreamPayload.of(
                runningTask.taskId(),
                resumeStage.name(),
                progress(resumeStage, true),
                "已加载可复用阶段结果，将从安全阶段继续"
        ));
        return new ScriptAiPipelineResumeState(
                chapterSummaries.get(),
                globalStoryState.orElse(null),
                scenePlan.orElse(null)
        );
    }

    private void saveStageResult(RunningTask runningTask, PipelineStage stage, Object result) {
        if (!runningTask.persistent() || !(result instanceof PipelineContext context)) {
            return;
        }
        switch (stage) {
            case CHAPTER_SUMMARY -> {
                if (!context.chapterSummaries().isEmpty()) {
                    stageResultService.saveChapterSummaries(runningTask.taskId(), context.chapterSummaries());
                }
            }
            case GLOBAL_STORY_EXTRACTION -> stageResultService.saveGlobalStoryState(
                    runningTask.taskId(),
                    context.globalStoryState()
            );
            case SCENE_PLANNING -> {
                if (context.scenePlan() != null) {
                    stageResultService.saveScenePlan(runningTask.taskId(), context.scenePlan());
                }
            }
            default -> {
            }
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

    private final class StreamObserver implements ScriptAiPipelineObserver {

        private final RunningTask runningTask;

        private StreamObserver(RunningTask runningTask) {
            this.runningTask = runningTask;
        }

        @Override
        public void onPipelineStart(PipelineContext context) {
            mark(PipelineStage.INIT, false, "章节解析完成，开始执行 AI Pipeline");
        }

        @Override
        public void onStageStart(PipelineContext context, PipelineStage stage) {
            mark(stage, true, stage.name() + " 阶段开始");
        }

        @Override
        public void onStageSuccess(PipelineContext context, PipelineStage stage) {
            mark(stage, false, stage.name() + " 阶段完成");
        }

        @Override
        public void onStageResult(PipelineContext context, PipelineStage stage, Object result) {
            saveStageResult(runningTask, stage, result);
        }

        @Override
        public void onStageFailure(PipelineContext context, PipelineStage stage, RuntimeException error) {
            send(ScriptYamlStreamEventType.MESSAGE, ScriptYamlStreamPayload.of(
                    runningTask.taskId(),
                    stage.name(),
                    progress(stage, false),
                    stage.name() + " 阶段失败，准备进入显式降级"
            ));
        }

        @Override
        public void onValidation(PipelineContext context, ScriptYamlValidationResult validationResult) {
            PipelineStage validationStage = validationStage(validationResult);
            ScriptYamlStreamService.send(runningTask.sender(), ScriptYamlStreamEventType.VALIDATION, ScriptYamlStreamPayload.validation(
                    runningTask.taskId(),
                    validationStage.name(),
                    progress(validationStage, false),
                    validationResult.status(),
                    validationResult.errorMessage(),
                    validationResult.valid()
            ));
        }

        @Override
        public void onFallback(PipelineContext context, PipelineStage fallbackStage, String reason) {
            send(ScriptYamlStreamEventType.MESSAGE, ScriptYamlStreamPayload.of(
                    runningTask.taskId(),
                    fallbackStage.name(),
                    progress(fallbackStage, false),
                    reason
            ));
        }

        @Override
        public void onComplete(ScriptAiPipelineResult result) {
            mark(result.context().currentStage(), false, "AI Pipeline 执行完成");
        }

        @Override
        public boolean isCanceled() {
            return runningTask.isCanceled();
        }

        private void mark(PipelineStage stage, boolean start, String message) {
            markRunning(runningTask, stage, progress(stage, start), message);
            send(ScriptYamlStreamEventType.PROGRESS, ScriptYamlStreamPayload.of(
                    runningTask.taskId(),
                    stage.name(),
                    progress(stage, start),
                    message
            ));
        }

        private PipelineStage validationStage(ScriptYamlValidationResult validationResult) {
            String errorMessage = validationResult == null ? null : validationResult.errorMessage();
            if (StringUtils.hasText(errorMessage)
                    && errorMessage.startsWith(ScriptYamlFactConstraintChecker.ERROR_PREFIX)) {
                return PipelineStage.FACT_CONSTRAINT_CHECK;
            }
            return PipelineStage.YAML_VALIDATION;
        }

        private void send(ScriptYamlStreamEventType eventType, Object payload) {
            ScriptYamlStreamService.send(runningTask.sender(), eventType, payload);
        }
    }

    private static final class RunningTask {

        private final AtomicBoolean canceled = new AtomicBoolean(false);
        private final AtomicReference<PipelineStage> currentStage = new AtomicReference<>(PipelineStage.INIT);
        private final AtomicReference<Integer> progress = new AtomicReference<>(0);
        private final SseEmitter emitter;
        private final boolean persistent;
        private final SseEmitterSender sender;
        private final String taskId;

        private RunningTask(String taskId, SseEmitter emitter, SseEmitterSender sender, boolean persistent) {
            this.taskId = taskId;
            this.emitter = emitter;
            this.sender = sender;
            this.persistent = persistent;
        }

        private String taskId() {
            return taskId;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private SseEmitterSender sender() {
            return sender;
        }

        private boolean persistent() {
            return persistent;
        }

        private PipelineStage currentStage() {
            return currentStage.get();
        }

        private int progress() {
            return progress.get();
        }

        private void update(PipelineStage stage, int nextProgress) {
            currentStage.set(stage);
            progress.updateAndGet(current -> Math.max(current, nextProgress));
        }

        private boolean isCanceled() {
            return canceled.get();
        }

        private boolean cancel() {
            return canceled.compareAndSet(false, true);
        }

        private void cancelSilently() {
            canceled.set(true);
        }
    }
}
