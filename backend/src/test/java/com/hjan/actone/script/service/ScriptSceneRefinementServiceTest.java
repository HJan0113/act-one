package com.hjan.actone.script.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjan.actone.ai.model.ChatRequest;
import com.hjan.actone.ai.model.ChatResponse;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.infra.ai.prompt.ClasspathPromptTemplateService;
import com.hjan.actone.script.dao.entity.ScriptDraftDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptDraftMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.model.ScriptSceneRefineRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScriptSceneRefinementServiceTest {

    private final ScriptDraftMapper scriptDraftMapper = mock(ScriptDraftMapper.class);
    private final ScriptProjectMapper scriptProjectMapper = mock(ScriptProjectMapper.class);
    private final RecordingLlmService llmService = new RecordingLlmService();
    private final ScriptSceneRefinementService service = new ScriptSceneRefinementService(
            scriptDraftMapper,
            scriptProjectMapper,
            new ScriptYamlValidator(),
            provider(llmService),
            new ClasspathPromptTemplateService(new DefaultResourceLoader()),
            new ObjectMapper()
    );

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser("user_1", "admin", "admin", ""));
        when(scriptProjectMapper.selectOne(any())).thenReturn(project("project_1", "user_1"));
        when(scriptDraftMapper.selectById("draft_1")).thenReturn(draft("draft_1", "project_1"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void refineAppliesScenePatchAndValidatesYamlBeforeReturning() {
        llmService.response = ChatResponse.text("""
                {
                  "sceneId": "scene_001",
                  "action": "林夏推门进入，停在旧剧场中央，手电光扫过幕布上的裂痕。",
                  "dialogues": [
                    {"speaker": "林夏", "emotion": "压低声音", "line": "这里有人来过。"}
                  ],
                  "aiExpansion": true,
                  "summary": "增强动作细节并补充一句低声对白。",
                  "riskWarnings": []
                }
                """);

        var response = service.refine(new ScriptSceneRefineRequest(
                "draft_1",
                "scene_001",
                "增强悬疑感，补一句对白"
        ));

        assertThat(response.validationStatus()).isEqualTo("valid");
        assertThat(response.validationError()).isNull();
        assertThat(response.scenePatch().summary()).contains("动作细节");
        assertThat(response.patchedYaml())
                .contains("林夏推门进入，停在旧剧场中央")
                .contains("这里有人来过")
                .contains("scene_002")
                .contains("旧剧场外的雨声压住脚步。");
        assertThat(llmService.lastRequest.messages().get(0).content())
                .contains("scene_001")
                .contains("增强悬疑感");
    }

    private ScriptDraftDO draft(String draftId, String projectId) {
        ScriptDraftDO draft = new ScriptDraftDO();
        draft.setId(draftId);
        draft.setProjectId(projectId);
        draft.setTitle("剧本草稿");
        draft.setSchemaVersion("1.0");
        draft.setYamlContent(validYaml());
        draft.setValidationStatus("valid");
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
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "ai_pipeline"
                characters:
                  - id: "character_001"
                    name: "林夏"
                    role: "主角"
                    description: "调查旧剧场的人。"
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
                    title: "旧剧场"
                    location: "旧剧场"
                    time: "夜晚"
                    scene_purpose: "建立冲突"
                    action: "林夏推门进入。"
                    dialogues: []
                    source_excerpt: "林夏推开旧剧场的门。"
                  - id: "scene_002"
                    act_id: "act_1"
                    chapter_index: 2
                    source_chapter_id: "chapter_2"
                    title: "雨夜"
                    location: "旧剧场外"
                    time: "夜晚"
                    scene_purpose: "推进追踪"
                    action: "旧剧场外的雨声压住脚步。"
                    dialogues: []
                    source_excerpt: "雨水打在铁门上。"
                """;
    }

    private ObjectProvider<LLMService> provider(LLMService llmService) {
        return new ObjectProvider<>() {
            @Override
            public LLMService getObject(Object... args) {
                return llmService;
            }

            @Override
            public LLMService getIfAvailable() {
                return llmService;
            }

            @Override
            public LLMService getObject() {
                return llmService;
            }
        };
    }

    private static final class RecordingLlmService implements LLMService {
        private ChatRequest lastRequest;
        private ChatResponse response;

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            return response;
        }
    }
}
