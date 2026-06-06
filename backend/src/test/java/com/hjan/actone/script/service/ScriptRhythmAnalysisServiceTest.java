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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScriptRhythmAnalysisServiceTest {

    private final ScriptDraftMapper scriptDraftMapper = mock(ScriptDraftMapper.class);
    private final ScriptProjectMapper scriptProjectMapper = mock(ScriptProjectMapper.class);

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
    void analyzeCalculatesRuleMetricsWhenLlmUnavailable() {
        ScriptRhythmAnalysisService service = newService(null);

        var response = service.analyze("draft_1");

        assertThat(response.assessmentMode()).isEqualTo("rule_based_fallback");
        assertThat(response.overall().sceneCount()).isEqualTo(3);
        assertThat(response.overall().dialogueCount()).isEqualTo(3);
        assertThat(response.overall().imbalanceSceneCount()).isGreaterThan(0);
        assertThat(response.scenes()).hasSize(3);
        assertThat(response.scenes().get(0).conflictScore()).isGreaterThanOrEqualTo(4);
        assertThat(response.scenes().get(2).warnings()).anyMatch(warning -> warning.contains("场景目的不清"));
        assertThat(response.suggestions()).isNotEmpty();
    }

    @Test
    void analyzeMergesAiAssessmentWhenLlmAvailable() {
        RecordingLlmService llmService = new RecordingLlmService();
        llmService.response = ChatResponse.text("""
                {
                  "sceneAssessments": [
                    {
                      "sceneId": "scene_001",
                      "conflictScore": 5,
                      "turningScore": 4,
                      "purposeScore": 5,
                      "summary": "强冲突场景，适合作为段落推进点。",
                      "warnings": []
                    },
                    {
                      "sceneId": "scene_003",
                      "conflictScore": 1,
                      "turningScore": 1,
                      "purposeScore": 1,
                      "summary": "目的和转折都偏弱。",
                      "warnings": ["建议补充明确阻碍。"]
                    }
                  ],
                  "suggestions": ["压缩等待段落，补强第三场阻碍。"]
                }
                """);
        ScriptRhythmAnalysisService service = newService(llmService);

        var response = service.analyze("draft_1");

        assertThat(response.assessmentMode()).isEqualTo("ai_assisted");
        assertThat(response.scenes().get(0).summary()).contains("强冲突场景");
        assertThat(response.scenes().get(2).warnings()).contains("建议补充明确阻碍。");
        assertThat(response.suggestions()).contains("压缩等待段落，补强第三场阻碍。");
        assertThat(llmService.lastRequest.messages().get(0).content())
                .contains("scenes_metrics_json")
                .doesNotContain("{{");
    }

    private ScriptRhythmAnalysisService newService(LLMService llmService) {
        return new ScriptRhythmAnalysisService(
                scriptDraftMapper,
                scriptProjectMapper,
                provider(llmService),
                new ClasspathPromptTemplateService(new DefaultResourceLoader()),
                new ObjectMapper()
        );
    }

    private ScriptDraftDO draft(String draftId, String projectId) {
        ScriptDraftDO draft = new ScriptDraftDO();
        draft.setId(draftId);
        draft.setProjectId(projectId);
        draft.setTitle("节奏测试草稿");
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
                  title: "节奏测试"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "ai_pipeline"
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
                    title: "走廊对峙"
                    location: "旧楼走廊"
                    time: "夜晚"
                    scene_purpose: "建立冲突并逼迫主角做出选择"
                    action: "林夏追到走廊尽头，门后传来威胁声。她停下脚步，意识到秘密已经暴露。"
                    dialogues:
                      - speaker: "林夏"
                        line: "别再往前。"
                      - speaker: "周屿"
                        line: "你已经没有选择。"
                    source_excerpt: "林夏追到走廊尽头。"
                  - id: "scene_002"
                    act_id: "act_1"
                    chapter_index: 2
                    source_chapter_id: "chapter_2"
                    title: "暗门发现"
                    location: "档案室"
                    time: "深夜"
                    scene_purpose: "推进调查并揭示新线索"
                    action: "林夏发现暗门背后的名单，突然决定返回旧楼。"
                    dialogues:
                      - speaker: "林夏"
                        line: "原来名单一直在这里。"
                    source_excerpt: "她发现暗门。"
                  - id: "scene_003"
                    act_id: "act_1"
                    chapter_index: 3
                    source_chapter_id: "chapter_3"
                    title: "等待"
                    location: "车站"
                    time: "清晨"
                    scene_purpose: "待补充"
                    action: "众人站在车站门口等待。"
                    dialogues: []
                    source_excerpt: "他们等待天亮。"
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
            public LLMService getIfUnique() {
                return llmService;
            }

            @Override
            public LLMService getObject() {
                return llmService;
            }
        };
    }

    private static class RecordingLlmService implements LLMService {

        private ChatRequest lastRequest;
        private ChatResponse response;

        @Override
        public ChatResponse chat(ChatRequest request) {
            lastRequest = request;
            return response;
        }
    }
}
