package com.hjan.actone.script.service;

import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.dao.entity.ScriptDraftDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptDraftMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.model.ScriptDraftSaveRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptDraftServiceTest {

    private final ScriptDraftMapper scriptDraftMapper = mock(ScriptDraftMapper.class);
    private final ScriptProjectMapper scriptProjectMapper = mock(ScriptProjectMapper.class);
    private final ScriptYamlValidator scriptYamlValidator = new ScriptYamlValidator();
    private final ScriptDraftService service = new ScriptDraftService(scriptDraftMapper, scriptProjectMapper, scriptYamlValidator);

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser("user_1", "admin", "admin", ""));
        when(scriptProjectMapper.selectOne(any())).thenReturn(project("project_1", "user_1"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void savePersistsYamlDraft() {
        when(scriptDraftMapper.insert(any(ScriptDraftDO.class))).thenAnswer(invocation -> {
            ScriptDraftDO draft = invocation.getArgument(0);
            draft.setId("draft_1");
            return 1;
        });

        var response = service.save(new ScriptDraftSaveRequest(
                " project_1 ",
                " 剧本草稿 ",
                "1.0",
                validYaml()
        ));

        assertThat(response.draftId()).isEqualTo("draft_1");
        assertThat(response.projectId()).isEqualTo("project_1");
        assertThat(response.title()).isEqualTo("剧本草稿");
        assertThat(response.validationStatus()).isEqualTo("valid");
        assertThat(response.validationError()).isNull();
        verify(scriptDraftMapper).insert(any(ScriptDraftDO.class));
    }

    @Test
    void saveStoresInvalidStatusWhenYamlCannotBeParsed() {
        when(scriptDraftMapper.insert(any(ScriptDraftDO.class))).thenAnswer(invocation -> {
            ScriptDraftDO draft = invocation.getArgument(0);
            draft.setId("draft_invalid");
            assertThat(draft.getValidationStatus()).isEqualTo("invalid");
            assertThat(draft.getValidationError()).contains("YAML 语法错误");
            return 1;
        });

        var response = service.save(new ScriptDraftSaveRequest(
                "project_1",
                "错误草稿",
                "1.0",
                "schema_version: ["
        ));

        assertThat(response.validationStatus()).isEqualTo("invalid");
        assertThat(response.validationError()).contains("YAML 语法错误");
    }

    @Test
    void listByProjectIdReturnsDraftSummaries() {
        ScriptDraftDO draft = draft("draft_1", "project_1");
        when(scriptDraftMapper.selectList(any())).thenReturn(List.of(draft));

        var responses = service.listByProjectId("project_1");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).draftId()).isEqualTo("draft_1");
        assertThat(responses.get(0).title()).isEqualTo("剧本草稿");
    }

    @Test
    void listByProjectIdRejectsProjectOwnedByOthers() {
        when(scriptProjectMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.listByProjectId("project_2"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void detailReturnsYamlContent() {
        when(scriptDraftMapper.selectById("draft_1")).thenReturn(draft("draft_1", "project_1"));

        var response = service.detail("draft_1");

        assertThat(response.draftId()).isEqualTo("draft_1");
        assertThat(response.yaml()).contains("schema_version");
    }

    @Test
    void detailRejectsMissingDraft() {
        when(scriptDraftMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.detail("missing"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("草稿不存在");
    }

    @Test
    void deleteOwnedDraftUsesLogicalDelete() {
        when(scriptDraftMapper.selectById("draft_1")).thenReturn(draft("draft_1", "project_1"));

        service.delete("draft_1");

        verify(scriptDraftMapper).deleteById("draft_1");
    }

    @Test
    void deleteRejectsMissingDraft() {
        when(scriptDraftMapper.selectById("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("草稿不存在");
    }

    private ScriptDraftDO draft(String draftId, String projectId) {
        ScriptDraftDO draft = new ScriptDraftDO();
        draft.setId(draftId);
        draft.setProjectId(projectId);
        draft.setTitle("剧本草稿");
        draft.setSchemaVersion("1.0");
        draft.setYamlContent(validYaml());
        draft.setValidationStatus("valid");
        draft.setCreateTime(LocalDateTime.of(2026, 6, 5, 10, 0));
        draft.setUpdateTime(LocalDateTime.of(2026, 6, 5, 11, 0));
        return draft;
    }

    private ScriptProjectDO project(String projectId, String userId) {
        ScriptProjectDO project = new ScriptProjectDO();
        project.setId(projectId);
        project.setTitle("测试项目");
        project.setCreatedBy(userId);
        return project;
    }

    private String validYaml() {
        return """
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "测试剧本"
                  author: "作者A"
                  logline: "主角完成选择。"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "rule_based_scaffold"
                characters: []
                acts:
                  - id: "act_1"
                    title: "第一幕"
                    chapter_range:
                      start: 1
                      end: 3
                scenes:
                  - id: "scene_001"
                    act_id: "act_1"
                    chapter_index: 1
                    source_chapter_id: "chapter_1"
                    title: "第 1 章"
                    location: "旧剧场"
                    time: "夜晚"
                    scene_purpose: "建立冲突"
                    action: "林夏推门进入。"
                    dialogues: []
                    source_excerpt: "林夏推开旧剧场的门。"
                """;
    }
}
