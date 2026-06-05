package com.hjan.actone.script.service;

import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.dao.entity.ScriptDraftDO;
import com.hjan.actone.script.dao.mapper.ScriptDraftMapper;
import com.hjan.actone.script.model.ScriptDraftSaveRequest;
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
    private final ScriptDraftService service = new ScriptDraftService(scriptDraftMapper);

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
                "schema_version: \"1.0\"\nscenes: []\n"
        ));

        assertThat(response.draftId()).isEqualTo("draft_1");
        assertThat(response.projectId()).isEqualTo("project_1");
        assertThat(response.title()).isEqualTo("剧本草稿");
        assertThat(response.validationStatus()).isEqualTo("unchecked");
        verify(scriptDraftMapper).insert(any(ScriptDraftDO.class));
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

    private ScriptDraftDO draft(String draftId, String projectId) {
        ScriptDraftDO draft = new ScriptDraftDO();
        draft.setId(draftId);
        draft.setProjectId(projectId);
        draft.setTitle("剧本草稿");
        draft.setSchemaVersion("1.0");
        draft.setYamlContent("schema_version: \"1.0\"\nscenes: []\n");
        draft.setValidationStatus("unchecked");
        draft.setCreateTime(LocalDateTime.of(2026, 6, 5, 10, 0));
        draft.setUpdateTime(LocalDateTime.of(2026, 6, 5, 11, 0));
        return draft;
    }
}
