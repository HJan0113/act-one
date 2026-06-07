package com.hjan.actone.script.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.dao.entity.ScriptConversionTaskDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptConversionTaskMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.model.ScriptConversionTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScriptConversionTaskService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELED = "canceled";

    private static final int MIN_PROGRESS = 0;
    private static final int MAX_RUNNING_PROGRESS = 99;
    private static final int COMPLETE_PROGRESS = 100;
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final int MAX_ERROR_LENGTH = 4_000;
    private static final Pattern API_KEY_PATTERN =
            Pattern.compile("(?i)(api[-_ ]?key\\s*[=:]\\s*)\\S+");
    private static final Pattern AUTHORIZATION_PATTERN =
            Pattern.compile("(?i)(authorization\\s*[=:]\\s*)\\S+");
    private static final Pattern MESSAGE_METADATA_PATTERN =
            Pattern.compile("([A-Za-z][A-Za-z0-9]*)=([^;]+)");

    private final ScriptConversionTaskMapper taskMapper;
    private final ScriptProjectMapper projectMapper;

    public ScriptConversionTaskService(ScriptConversionTaskMapper taskMapper,
                                       ScriptProjectMapper projectMapper) {
        this.taskMapper = Objects.requireNonNull(taskMapper, "ScriptConversionTaskMapper 不能为空");
        this.projectMapper = Objects.requireNonNull(projectMapper, "ScriptProjectMapper 不能为空");
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptConversionTaskResponse create(String projectId, String schemaVersion, String modelId) {
        String normalizedProjectId = requireText(projectId, "项目 ID 不能为空。");
        verifyProjectOwned(normalizedProjectId);

        ScriptConversionTaskDO task = new ScriptConversionTaskDO();
        task.setProjectId(normalizedProjectId);
        task.setStatus(STATUS_PENDING);
        task.setProgress(MIN_PROGRESS);
        task.setMessage(stageMessage(PipelineStage.INIT, "等待开始"));
        task.setSchemaVersion(defaultText(schemaVersion, ScriptYamlDraftService.SCHEMA_VERSION));
        task.setModelId(trimToNull(modelId));
        taskMapper.insert(task);
        return toResponse(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptConversionTaskResponse createOrReuse(String projectId,
                                                      String schemaVersion,
                                                      String modelId,
                                                      boolean forceNew) {
        if (!forceNew) {
            Optional<ScriptConversionTaskResponse> reusable = findReusable(projectId);
            if (reusable.isPresent()) {
                return reusable.get();
            }
        }
        return create(projectId, schemaVersion, modelId);
    }

    public Optional<ScriptConversionTaskResponse> findReusable(String projectId) {
        String normalizedProjectId = requireText(projectId, "项目 ID 不能为空。");
        verifyProjectOwned(normalizedProjectId);
        List<ScriptConversionTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<ScriptConversionTaskDO>()
                .eq(ScriptConversionTaskDO::getProjectId, normalizedProjectId)
                .in(ScriptConversionTaskDO::getStatus, STATUS_PENDING, STATUS_RUNNING)
                .orderByDesc(ScriptConversionTaskDO::getCreateTime)
                .last("LIMIT 1"));
        if (tasks == null || tasks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toResponse(tasks.get(0)));
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptConversionTaskResponse markRunning(String taskId,
                                                   PipelineStage stage,
                                                   int progress,
                                                   String message) {
        ScriptConversionTaskDO task = requireOwnedTask(taskId);
        if (isTerminal(task.getStatus())) {
            return toResponse(task);
        }
        task.setStatus(STATUS_RUNNING);
        task.setProgress(clamp(progress, MIN_PROGRESS, MAX_RUNNING_PROGRESS));
        task.setMessage(stageMessage(stage, defaultText(message, "阶段执行中")));
        task.setErrorMessage(null);
        if (task.getStartTime() == null) {
            task.setStartTime(LocalDateTime.now());
        }
        taskMapper.updateById(task);
        return toResponse(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptConversionTaskResponse markSuccess(String taskId, String draftId, String message) {
        ScriptConversionTaskDO task = requireOwnedTask(taskId);
        if (isTerminal(task.getStatus())) {
            return toResponse(task);
        }
        task.setDraftId(trimToNull(draftId));
        task.setStatus(STATUS_SUCCESS);
        task.setProgress(COMPLETE_PROGRESS);
        task.setMessage(stageMessage(PipelineStage.COMPLETED, defaultText(message, "转换完成")));
        task.setErrorMessage(null);
        task.setEndTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return toResponse(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptConversionTaskResponse markFailed(String taskId,
                                                  PipelineStage stage,
                                                  String errorMessage) {
        return markFailed(taskId, stage, errorMessage, RuntimeException.class.getSimpleName(), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptConversionTaskResponse markFailed(String taskId,
                                                  PipelineStage stage,
                                                  RuntimeException error) {
        RuntimeException actualError = Objects.requireNonNull(error, "RuntimeException 不能为空");
        return markFailed(
                taskId,
                stage,
                actualError.getMessage(),
                actualError.getClass().getSimpleName(),
                isRetryable(actualError)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptConversionTaskResponse retryFailed(String taskId) {
        ScriptConversionTaskDO task = requireOwnedTask(taskId);
        if (!STATUS_FAILED.equals(task.getStatus())) {
            throw new ClientException("只有失败任务可以重试。");
        }
        ScriptConversionTaskResponse failed = toResponse(task);
        if (!Boolean.TRUE.equals(failed.retryable())) {
            throw new ClientException("当前失败不可重试，请检查输入或配置后新建任务。");
        }
        PipelineStage resumeStage = parseStage(failed.resumeStage()).orElse(PipelineStage.INIT);
        task.setStatus(STATUS_PENDING);
        task.setProgress(MIN_PROGRESS);
        task.setMessage(stageMessage(
                resumeStage,
                "retryOf=" + defaultText(failed.failedStage(), PipelineStage.INIT.name()) + "; 从安全阶段继续"
        ));
        task.setErrorMessage(null);
        task.setStartTime(null);
        task.setEndTime(null);
        taskMapper.updateById(task);
        return toResponse(task);
    }

    private ScriptConversionTaskResponse markFailed(String taskId,
                                                    PipelineStage stage,
                                                    String errorMessage,
                                                    String errorType,
                                                    boolean retryable) {
        ScriptConversionTaskDO task = requireOwnedTask(taskId);
        if (isTerminal(task.getStatus())) {
            return toResponse(task);
        }
        String safeError = sanitize(errorMessage, "转换失败", MAX_ERROR_LENGTH);
        PipelineStage safeStage = stage == null ? PipelineStage.INIT : stage;
        PipelineStage resumeStage = resolveResumeStage(safeStage);
        task.setStatus(STATUS_FAILED);
        task.setMessage(failureMessage(safeStage, errorType, retryable, resumeStage));
        task.setErrorMessage(safeError);
        task.setEndTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return toResponse(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptConversionTaskResponse markCanceled(String taskId,
                                                    PipelineStage stage,
                                                    String message) {
        ScriptConversionTaskDO task = requireOwnedTask(taskId);
        if (isTerminal(task.getStatus())) {
            return toResponse(task);
        }
        task.setStatus(STATUS_CANCELED);
        task.setMessage(stageMessage(stage, defaultText(message, "用户已取消")));
        task.setEndTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return toResponse(task);
    }

    public ScriptConversionTaskResponse detail(String taskId) {
        return toResponse(requireOwnedTask(taskId));
    }

    private ScriptConversionTaskDO requireOwnedTask(String taskId) {
        String normalizedTaskId = requireText(taskId, "任务 ID 不能为空。");
        ScriptConversionTaskDO task = taskMapper.selectById(normalizedTaskId);
        if (task == null) {
            throw new ClientException("转换任务不存在。");
        }
        verifyProjectOwned(task.getProjectId());
        return task;
    }

    private void verifyProjectOwned(String projectId) {
        ScriptProjectDO project = projectMapper.selectOne(new LambdaQueryWrapper<ScriptProjectDO>()
                .eq(ScriptProjectDO::getId, projectId)
                .eq(ScriptProjectDO::getCreatedBy, UserContext.requireUser().userId()));
        if (project == null) {
            throw new ClientException("项目不存在或无权访问");
        }
    }

    private ScriptConversionTaskResponse toResponse(ScriptConversionTaskDO task) {
        Map<String, String> metadata = parseMessageMetadata(task.getMessage());
        boolean failed = STATUS_FAILED.equals(task.getStatus());
        return new ScriptConversionTaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getDraftId(),
                task.getStatus(),
                task.getProgress(),
                task.getMessage(),
                task.getErrorMessage(),
                failed ? metadata.get("stage") : null,
                failed ? metadata.get("errorType") : null,
                failed ? Boolean.valueOf(metadata.getOrDefault("retryable", "false")) : null,
                failed ? metadata.get("resumeStage") : null,
                task.getSchemaVersion(),
                task.getModelId(),
                task.getStartTime(),
                task.getEndTime(),
                task.getCreateTime(),
                task.getUpdateTime()
        );
    }

    private boolean isTerminal(String status) {
        return STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status) || STATUS_CANCELED.equals(status);
    }

    private String stageMessage(PipelineStage stage, String message) {
        PipelineStage safeStage = Objects.requireNonNull(stage, "PipelineStage 不能为空");
        return sanitize("stage=" + safeStage.name() + "; " + defaultText(message, ""), "", MAX_MESSAGE_LENGTH);
    }

    private String failureMessage(PipelineStage stage, String errorType, boolean retryable, PipelineStage resumeStage) {
        return stageMessage(
                stage,
                "errorType=" + defaultText(errorType, RuntimeException.class.getSimpleName())
                        + "; retryable=" + retryable
                        + "; resumeStage=" + resumeStage.name()
                        + "; 转换失败"
        );
    }

    private PipelineStage resolveResumeStage(PipelineStage failedStage) {
        return switch (failedStage) {
            case INIT, CHAPTER_SUMMARY -> PipelineStage.CHAPTER_SUMMARY;
            case SCENE_PLANNING -> PipelineStage.SCENE_PLANNING;
            case SCRIPT_YAML_GENERATION, YAML_VALIDATION, COMPLETED -> PipelineStage.SCRIPT_YAML_GENERATION;
        };
    }

    private boolean isRetryable(RuntimeException error) {
        return !(error instanceof ClientException || error instanceof IllegalArgumentException);
    }

    private Optional<PipelineStage> parseStage(String stageName) {
        if (!StringUtils.hasText(stageName)) {
            return Optional.empty();
        }
        try {
            return Optional.of(PipelineStage.valueOf(stageName.trim()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Map<String, String> parseMessageMetadata(String message) {
        Map<String, String> metadata = new HashMap<>();
        if (!StringUtils.hasText(message)) {
            return metadata;
        }
        Matcher matcher = MESSAGE_METADATA_PATTERN.matcher(message);
        while (matcher.find()) {
            metadata.put(matcher.group(1), matcher.group(2).trim());
        }
        return metadata;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException(message);
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String sanitize(String value, String fallback, int maxLength) {
        String sanitized = defaultText(value, fallback);
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("$1***");
        sanitized = AUTHORIZATION_PATTERN.matcher(sanitized).replaceAll("$1***");
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength).stripTrailing();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
