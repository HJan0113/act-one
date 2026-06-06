package com.hjan.actone.script.service;

import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.dao.entity.ScriptConversionTaskDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptConversionTaskMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptConversionTaskServiceTest {

    private final ScriptConversionTaskMapper taskMapper = mock(ScriptConversionTaskMapper.class);
    private final ScriptProjectMapper projectMapper = mock(ScriptProjectMapper.class);
    private final ScriptConversionTaskService service = new ScriptConversionTaskService(taskMapper, projectMapper);

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser("user_1", "admin", "admin", ""));
        when(projectMapper.selectOne(any())).thenReturn(project("project_1", "user_1"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createPersistsPendingTaskForOwnedProject() {
        when(taskMapper.insert(any(ScriptConversionTaskDO.class))).thenAnswer(invocation -> {
            ScriptConversionTaskDO task = invocation.getArgument(0);
            task.setId("task_1");
            return 1;
        });

        var response = service.create(" project_1 ", " 1.0 ", " qwen3-max ");

        assertThat(response.taskId()).isEqualTo("task_1");
        assertThat(response.projectId()).isEqualTo("project_1");
        assertThat(response.status()).isEqualTo(ScriptConversionTaskService.STATUS_PENDING);
        assertThat(response.progress()).isZero();
        assertThat(response.schemaVersion()).isEqualTo("1.0");
        assertThat(response.modelId()).isEqualTo("qwen3-max");
        assertThat(response.message()).contains("stage=INIT");
        verify(taskMapper).insert(any(ScriptConversionTaskDO.class));
    }

    @Test
    void markRunningRecordsStageAndStartTime() {
        ScriptConversionTaskDO task = task("task_1", "project_1");
        when(taskMapper.selectById("task_1")).thenReturn(task);

        var response = service.markRunning("task_1", PipelineStage.CHAPTER_SUMMARY, 12, "章节摘要生成中");

        assertThat(response.status()).isEqualTo(ScriptConversionTaskService.STATUS_RUNNING);
        assertThat(response.progress()).isEqualTo(12);
        assertThat(response.message())
                .contains("stage=CHAPTER_SUMMARY")
                .contains("章节摘要生成中");
        assertThat(response.startTime()).isNotNull();
        assertThat(response.errorMessage()).isNull();
        verify(taskMapper).updateById(task);
    }

    @Test
    void markRunningClampsProgressBeforeCompletion() {
        ScriptConversionTaskDO task = task("task_1", "project_1");
        when(taskMapper.selectById("task_1")).thenReturn(task);

        var response = service.markRunning("task_1", PipelineStage.SCRIPT_YAML_GENERATION, 150, "生成 YAML");

        assertThat(response.progress()).isEqualTo(99);
    }

    @Test
    void markSuccessStoresDraftAndEndTime() {
        ScriptConversionTaskDO task = task("task_1", "project_1");
        task.setStartTime(LocalDateTime.of(2026, 6, 6, 10, 0));
        when(taskMapper.selectById("task_1")).thenReturn(task);

        var response = service.markSuccess("task_1", " draft_1 ", "生成完成");

        assertThat(response.status()).isEqualTo(ScriptConversionTaskService.STATUS_SUCCESS);
        assertThat(response.progress()).isEqualTo(100);
        assertThat(response.draftId()).isEqualTo("draft_1");
        assertThat(response.message()).contains("stage=COMPLETED");
        assertThat(response.endTime()).isNotNull();
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void markFailedStoresSanitizedErrorAndEndTime() {
        ScriptConversionTaskDO task = task("task_1", "project_1");
        when(taskMapper.selectById("task_1")).thenReturn(task);

        var response = service.markFailed(
                "task_1",
                PipelineStage.SCRIPT_YAML_GENERATION,
                "provider failed api-key=secret123 authorization=Bearer-token 完整小说正文"
        );

        assertThat(response.status()).isEqualTo(ScriptConversionTaskService.STATUS_FAILED);
        assertThat(response.message()).contains("stage=SCRIPT_YAML_GENERATION");
        assertThat(response.errorMessage())
                .contains("api-key=***")
                .contains("authorization=***")
                .doesNotContain("secret123", "Bearer-token");
        assertThat(response.endTime()).isNotNull();
    }

    @Test
    void markCanceledStoresCanceledStatus() {
        ScriptConversionTaskDO task = task("task_1", "project_1");
        when(taskMapper.selectById("task_1")).thenReturn(task);

        var response = service.markCanceled("task_1", PipelineStage.SCENE_PLANNING, "用户停止生成");

        assertThat(response.status()).isEqualTo(ScriptConversionTaskService.STATUS_CANCELED);
        assertThat(response.message())
                .contains("stage=SCENE_PLANNING")
                .contains("用户停止生成");
        assertThat(response.endTime()).isNotNull();
    }

    @Test
    void detailRejectsMissingTask() {
        when(taskMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.detail("missing"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("转换任务不存在");
    }

    @Test
    void createRejectsProjectOwnedByOthers() {
        when(projectMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.create("project_2", "1.0", null))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void createTruncatesLongMessageToFitTaskTable() {
        when(taskMapper.insert(any(ScriptConversionTaskDO.class))).thenAnswer(invocation -> {
            ScriptConversionTaskDO task = invocation.getArgument(0);
            task.setId("task_1");
            return 1;
        });
        ArgumentCaptor<ScriptConversionTaskDO> captor = ArgumentCaptor.forClass(ScriptConversionTaskDO.class);

        service.create("project_1", "1.0", null);

        verify(taskMapper).insert(captor.capture());
        assertThat(captor.getValue().getMessage()).hasSizeLessThanOrEqualTo(512);
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
}
