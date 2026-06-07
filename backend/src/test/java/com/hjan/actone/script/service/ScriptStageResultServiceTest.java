package com.hjan.actone.script.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.dao.entity.ScriptConversionTaskDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.entity.ScriptStageResultDO;
import com.hjan.actone.script.dao.mapper.ScriptConversionTaskMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.dao.mapper.ScriptStageResultMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptStageResultServiceTest {

    private final ScriptStageResultMapper stageResultMapper = mock(ScriptStageResultMapper.class);
    private final ScriptConversionTaskMapper taskMapper = mock(ScriptConversionTaskMapper.class);
    private final ScriptProjectMapper projectMapper = mock(ScriptProjectMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScriptStageResultService service = new ScriptStageResultService(
            stageResultMapper,
            taskMapper,
            projectMapper,
            objectMapper
    );

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser("user_1", "admin", "admin", ""));
        when(taskMapper.selectById("task_1")).thenReturn(task("task_1", "project_1"));
        when(projectMapper.selectOne(any())).thenReturn(project("project_1", "user_1"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void saveChapterSummariesInsertsStageResult() {
        when(stageResultMapper.selectOne(any())).thenReturn(null);
        when(stageResultMapper.insert(any(ScriptStageResultDO.class))).thenAnswer(invocation -> {
            ScriptStageResultDO result = invocation.getArgument(0);
            result.setId("result_1");
            return 1;
        });
        ArgumentCaptor<ScriptStageResultDO> captor = ArgumentCaptor.forClass(ScriptStageResultDO.class);

        var response = service.saveChapterSummaries("task_1", List.of(chapterSummary("chapter_1", "第一章摘要")));

        assertThat(response.resultId()).isEqualTo("result_1");
        assertThat(response.taskId()).isEqualTo("task_1");
        assertThat(response.stage()).isEqualTo(PipelineStage.CHAPTER_SUMMARY.name());
        assertThat(response.resultHash()).isNotBlank();
        assertThat(response.attempt()).isEqualTo(1);
        assertThat(response.reused()).isFalse();
        verify(stageResultMapper).insert(captor.capture());
        assertThat(captor.getValue().getStage()).isEqualTo(PipelineStage.CHAPTER_SUMMARY.name());
        assertThat(captor.getValue().getResultJson()).contains("chapter_1", "第一章摘要");
    }

    @Test
    void saveSameResultReusesExistingRow() throws Exception {
        List<ChapterSummary> summaries = List.of(chapterSummary("chapter_1", "第一章摘要"));
        String json = objectMapper.writeValueAsString(summaries);
        ScriptStageResultDO existing = stageResult(
                "result_1",
                PipelineStage.CHAPTER_SUMMARY,
                json,
                sha256(json),
                2
        );
        when(stageResultMapper.selectOne(any())).thenReturn(existing);

        var response = service.saveChapterSummaries("task_1", summaries);

        assertThat(response.resultId()).isEqualTo("result_1");
        assertThat(response.attempt()).isEqualTo(2);
        assertThat(response.reused()).isTrue();
        verify(stageResultMapper, never()).insert(any(ScriptStageResultDO.class));
        verify(stageResultMapper, never()).updateById(any(ScriptStageResultDO.class));
    }

    @Test
    void saveChangedResultUpdatesExistingAttempt() throws Exception {
        String oldJson = objectMapper.writeValueAsString(List.of(chapterSummary("chapter_1", "旧摘要")));
        ScriptStageResultDO existing = stageResult(
                "result_1",
                PipelineStage.CHAPTER_SUMMARY,
                oldJson,
                sha256(oldJson),
                2
        );
        when(stageResultMapper.selectOne(any())).thenReturn(existing);
        ArgumentCaptor<ScriptStageResultDO> captor = ArgumentCaptor.forClass(ScriptStageResultDO.class);

        var response = service.saveChapterSummaries("task_1", List.of(chapterSummary("chapter_1", "新摘要")));

        assertThat(response.resultId()).isEqualTo("result_1");
        assertThat(response.attempt()).isEqualTo(3);
        assertThat(response.reused()).isFalse();
        verify(stageResultMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAttempt()).isEqualTo(3);
        assertThat(captor.getValue().getResultJson()).contains("新摘要").doesNotContain("旧摘要");
    }

    @Test
    void loadScenePlanDeserializesStoredPayload() throws Exception {
        ScenePlan scenePlan = scenePlan();
        String json = objectMapper.writeValueAsString(scenePlan);
        when(stageResultMapper.selectOne(any())).thenReturn(stageResult(
                "result_1",
                PipelineStage.SCENE_PLANNING,
                json,
                sha256(json),
                1
        ));

        Optional<ScenePlan> loaded = service.loadScenePlan("task_1");

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().scenes()).hasSize(1);
        assertThat(loaded.orElseThrow().scenes().get(0).id()).isEqualTo("scene_001");
    }

    @Test
    void loadMissingStageReturnsEmpty() {
        when(stageResultMapper.selectOne(any())).thenReturn(null);

        Optional<GlobalStoryState> loaded = service.loadGlobalStoryState("task_1");

        assertThat(loaded).isEmpty();
    }

    @Test
    void saveRejectsTaskOwnedByOthers() {
        when(projectMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.saveScenePlan("task_1", scenePlan()))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("无权访问");
        verify(stageResultMapper, never()).insert(any(ScriptStageResultDO.class));
        verify(stageResultMapper, never()).updateById(any(ScriptStageResultDO.class));
    }

    @Test
    void saveRejectsMissingTask() {
        when(taskMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.saveGlobalStoryState("missing", GlobalStoryState.empty()))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("转换任务不存在");
    }

    @Test
    void saveRejectsEmptyStageResultPayloads() {
        assertThatThrownBy(() -> service.saveChapterSummaries("task_1", List.of()))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("章节摘要不能为空");
        assertThatThrownBy(() -> service.saveGlobalStoryState("task_1", null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("全局故事状态不能为空");
        assertThatThrownBy(() -> service.saveScenePlan("task_1", null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("场景规划不能为空");
    }

    private ChapterSummary chapterSummary(String chapterId, String summary) {
        SourceEvidence evidence = new SourceEvidence(chapterId, 1, "林夜推开破庙木门。");
        return new ChapterSummary(
                chapterId,
                1,
                "雨夜破庙",
                summary,
                List.of(new ChapterSummary.KeyEvent("event_001", "林夜遭遇任务反转。", List.of(evidence))),
                List.of("林夜"),
                List.of("荒山破庙"),
                List.of("雨夜子时"),
                List.of("刺杀任务与保护弱者之间的选择"),
                List.of(new ChapterSummary.InnerState("林夜", "他开始怀疑任务真相。", evidence)),
                List.of(evidence)
        );
    }

    private ScenePlan scenePlan() {
        return new ScenePlan(List.of(new ScenePlan.Scene(
                "scene_001",
                "act_1",
                1,
                "chapter_1",
                "雨夜破庙",
                "荒山破庙",
                "雨夜",
                List.of("林夜"),
                "让主角第一次面对任务反转。",
                "刺杀任务与保护弱者的冲突。",
                List.of(new ScenePlan.Beat("林夜推门进入破庙。", "林夜推开破庙木门。", List.of("林夜"))),
                "林夜推开破庙木门。"
        )));
    }

    private ScriptStageResultDO stageResult(String resultId,
                                           PipelineStage stage,
                                           String json,
                                           String hash,
                                           int attempt) {
        ScriptStageResultDO result = new ScriptStageResultDO();
        result.setId(resultId);
        result.setTaskId("task_1");
        result.setStage(stage.name());
        result.setResultJson(json);
        result.setResultHash(hash);
        result.setAttempt(attempt);
        return result;
    }

    private ScriptConversionTaskDO task(String taskId, String projectId) {
        ScriptConversionTaskDO task = new ScriptConversionTaskDO();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(ScriptConversionTaskService.STATUS_PENDING);
        task.setProgress(0);
        task.setSchemaVersion("1.0");
        task.setMessage("stage=INIT; 等待开始");
        return task;
    }

    private ScriptProjectDO project(String projectId, String userId) {
        ScriptProjectDO project = new ScriptProjectDO();
        project.setId(projectId);
        project.setTitle("测试项目");
        project.setCreatedBy(userId);
        return project;
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
