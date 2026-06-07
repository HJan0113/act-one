package com.hjan.actone.script.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.dao.entity.ScriptConversionTaskDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.entity.ScriptStageResultDO;
import com.hjan.actone.script.dao.mapper.ScriptConversionTaskMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.dao.mapper.ScriptStageResultMapper;
import com.hjan.actone.script.model.ScriptStageResultResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ScriptStageResultService {

    private static final TypeReference<List<ChapterSummary>> CHAPTER_SUMMARIES_TYPE =
            new TypeReference<>() {
            };

    private final ScriptStageResultMapper stageResultMapper;
    private final ScriptConversionTaskMapper taskMapper;
    private final ScriptProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    public ScriptStageResultService(ScriptStageResultMapper stageResultMapper,
                                    ScriptConversionTaskMapper taskMapper,
                                    ScriptProjectMapper projectMapper,
                                    ObjectMapper objectMapper) {
        this.stageResultMapper = Objects.requireNonNull(stageResultMapper, "ScriptStageResultMapper 不能为空");
        this.taskMapper = Objects.requireNonNull(taskMapper, "ScriptConversionTaskMapper 不能为空");
        this.projectMapper = Objects.requireNonNull(projectMapper, "ScriptProjectMapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptStageResultResponse saveChapterSummaries(String taskId, List<ChapterSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            throw new ClientException("章节摘要不能为空。");
        }
        return save(taskId, PipelineStage.CHAPTER_SUMMARY, summaries);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptStageResultResponse saveGlobalStoryState(String taskId, GlobalStoryState state) {
        if (state == null) {
            throw new ClientException("全局故事状态不能为空。");
        }
        return save(taskId, PipelineStage.GLOBAL_STORY_EXTRACTION, state);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptStageResultResponse saveScenePlan(String taskId, ScenePlan scenePlan) {
        if (scenePlan == null) {
            throw new ClientException("场景规划不能为空。");
        }
        return save(taskId, PipelineStage.SCENE_PLANNING, scenePlan);
    }

    public Optional<List<ChapterSummary>> loadChapterSummaries(String taskId) {
        return load(taskId, PipelineStage.CHAPTER_SUMMARY, CHAPTER_SUMMARIES_TYPE);
    }

    public Optional<GlobalStoryState> loadGlobalStoryState(String taskId) {
        return load(taskId, PipelineStage.GLOBAL_STORY_EXTRACTION, GlobalStoryState.class);
    }

    public Optional<ScenePlan> loadScenePlan(String taskId) {
        return load(taskId, PipelineStage.SCENE_PLANNING, ScenePlan.class);
    }

    private ScriptStageResultResponse save(String taskId, PipelineStage stage, Object result) {
        requireOwnedTask(taskId);
        String json = serialize(result);
        String hash = sha256(json);
        ScriptStageResultDO existing = selectOne(taskId, stage);
        if (existing != null && hash.equals(existing.getResultHash())) {
            return toResponse(existing, true);
        }

        ScriptStageResultDO target = existing == null ? new ScriptStageResultDO() : existing;
        target.setTaskId(requireText(taskId, "任务 ID 不能为空。"));
        target.setStage(stage.name());
        target.setResultJson(json);
        target.setResultHash(hash);
        target.setAttempt(existing == null ? 1 : safeAttempt(existing.getAttempt()) + 1);

        if (existing == null) {
            stageResultMapper.insert(target);
        } else {
            stageResultMapper.updateById(target);
        }
        return toResponse(target, false);
    }

    private <T> Optional<T> load(String taskId, PipelineStage stage, Class<T> targetType) {
        requireOwnedTask(taskId);
        ScriptStageResultDO result = selectOne(taskId, stage);
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(result.getResultJson(), targetType));
    }

    private <T> Optional<T> load(String taskId, PipelineStage stage, TypeReference<T> targetType) {
        requireOwnedTask(taskId);
        ScriptStageResultDO result = selectOne(taskId, stage);
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(result.getResultJson(), targetType));
    }

    private ScriptStageResultDO selectOne(String taskId, PipelineStage stage) {
        return stageResultMapper.selectOne(new LambdaQueryWrapper<ScriptStageResultDO>()
                .eq(ScriptStageResultDO::getTaskId, requireText(taskId, "任务 ID 不能为空。"))
                .eq(ScriptStageResultDO::getStage, stage.name()));
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

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("阶段结果序列化失败", ex);
        }
    }

    private <T> T deserialize(String json, Class<T> targetType) {
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("阶段结果反序列化失败", ex);
        }
    }

    private <T> T deserialize(String json, TypeReference<T> targetType) {
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("阶段结果反序列化失败", ex);
        }
    }

    private ScriptStageResultResponse toResponse(ScriptStageResultDO result, boolean reused) {
        return new ScriptStageResultResponse(
                result.getId(),
                result.getTaskId(),
                result.getStage(),
                result.getResultHash(),
                result.getAttempt(),
                reused,
                result.getCreateTime(),
                result.getUpdateTime()
        );
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException(message);
        }
        return value.trim();
    }

    private int safeAttempt(Integer attempt) {
        return attempt == null || attempt < 1 ? 1 : attempt;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }
}
