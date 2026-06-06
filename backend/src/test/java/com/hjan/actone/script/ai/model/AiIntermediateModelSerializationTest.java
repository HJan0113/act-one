package com.hjan.actone.script.ai.model;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiIntermediateModelSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chapterSummaryRoundTripsJsonAndKeepsSourceEvidence() throws Exception {
        ChapterSummary summary = chapterSummary();

        String json = objectMapper.writeValueAsString(summary);
        ChapterSummary restored = objectMapper.readValue(json, ChapterSummary.class);

        assertThat(json)
                .contains("\"chapterId\":\"chapter_1\"")
                .contains("\"sourceChapterId\":\"chapter_1\"");
        assertThat(restored).isEqualTo(summary);
        assertThat(restored.sourceEvidence().get(0).excerpt()).contains("暗门");
        assertThatThrownBy(() -> restored.characters().add("不可修改"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void globalStoryStateRoundTripsJsonWithStableIds() throws Exception {
        GlobalStoryState state = globalStoryState();

        String json = objectMapper.writeValueAsString(state);
        GlobalStoryState restored = objectMapper.readValue(json, GlobalStoryState.class);

        assertThat(restored).isEqualTo(state);
        assertThat(restored.characters().get(0).id()).isEqualTo("character_001");
        assertThat(restored.timeline().get(0).chapterId()).isEqualTo("chapter_1");
        assertThat(restored.conflicts().get(0).candidateIds()).containsExactly("character_002", "character_003");
    }

    @Test
    void scenePlanRoundTripsJsonAndRequiresTraceableScenes() throws Exception {
        ScenePlan scenePlan = scenePlan();

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(scenePlan);
        ScenePlan restored = objectMapper.readValue(json, ScenePlan.class);

        assertThat(restored).isEqualTo(scenePlan);
        assertThat(restored.scenes().get(0).sourceChapterId()).isEqualTo("chapter_1");
        assertThat(restored.scenes().get(0).beats().get(0).sourceExcerpt()).contains("钥匙");
    }

    @Test
    void pipelineContextTracksStageTransitionsAndYamlOutput() throws Exception {
        PipelineContext context = PipelineContext.create(
                        "project_1",
                        "暗门",
                        "测试作者",
                        "林夏追查旧剧场的秘密。",
                        3
                )
                .withChapterSummaries(List.of(chapterSummary()))
                .withGlobalStoryState(globalStoryState())
                .withScenePlan(scenePlan())
                .withGeneratedYaml("""
                        schema_version: "1.0"
                        kind: "screenplay_draft"
                        """)
                .withValidation("valid", null)
                .completed();

        String json = objectMapper.writeValueAsString(context);
        PipelineContext restored = objectMapper.readValue(json, PipelineContext.class);

        assertThat(restored.currentStage()).isEqualTo(PipelineStage.COMPLETED);
        assertThat(restored.taskId()).isEqualTo(context.taskId());
        assertThat(restored.taskId()).startsWith("task_");
        assertThat(restored.chapterSummaries()).hasSize(1);
        assertThat(restored.globalStoryState().characters()).hasSize(1);
        assertThat(restored.scenePlan().scenes()).hasSize(1);
        assertThat(restored.generatedYaml()).contains("screenplay_draft");
        assertThat(restored.validationStatus()).isEqualTo("valid");
    }

    @Test
    void pipelineContextDefaultsOptionalStageOutputs() {
        PipelineContext context = PipelineContext.create("project_1", "暗门", null, "  ", 3);

        assertThat(context.currentStage()).isEqualTo(PipelineStage.INIT);
        assertThat(context.taskId()).startsWith("task_");
        assertThat(context.author()).isNull();
        assertThat(context.logline()).isNull();
        assertThat(context.chapterSummaries()).isEmpty();
        assertThat(context.globalStoryState()).isEqualTo(GlobalStoryState.empty());
        assertThat(context.scenePlan()).isNull();
    }

    @Test
    void rejectsInvalidIntermediateStructures() {
        assertThatThrownBy(() -> new SourceEvidence("chapter_1", 0, "摘录"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("chapterIndex 必须大于 0");

        assertThatThrownBy(() -> new ScenePlan(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scenes 不能为空");

        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "chapterId": "chapter_1",
                  "chapterIndex": 1,
                  "title": "第一章 暗门",
                  "summary": "林夏找到暗门。",
                  "keyEvents": [],
                  "characters": [],
                  "locations": [],
                  "timeExpressions": [],
                  "conflicts": [],
                  "innerStates": [],
                  "sourceEvidence": []
                }
                """, ChapterSummary.class))
                .isInstanceOf(JsonMappingException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private ChapterSummary chapterSummary() {
        SourceEvidence evidence = evidence("林夏在旧剧场后台发现一扇暗门。");
        return new ChapterSummary(
                "chapter_1",
                1,
                "第一章 暗门",
                "林夏进入旧剧场，发现暗门和失踪道具箱的线索。",
                List.of(new ChapterSummary.KeyEvent(
                        "event_001",
                        "林夏发现暗门。",
                        List.of(evidence)
                )),
                List.of("林夏"),
                List.of("旧剧场后台"),
                List.of("深夜"),
                List.of("林夏必须决定是否独自进入暗门。"),
                List.of(new ChapterSummary.InnerState(
                        "林夏",
                        "她害怕却不愿退缩。",
                        evidence
                )),
                List.of(evidence)
        );
    }

    private GlobalStoryState globalStoryState() {
        return new GlobalStoryState(
                List.of(new GlobalStoryState.CharacterProfile(
                        "character_001",
                        "林夏",
                        List.of("小夏"),
                        "主角",
                        "追查旧剧场秘密的年轻编剧。",
                        "chapter_1",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.LocationProfile(
                        "location_001",
                        "旧剧场后台",
                        "尘封多年，通向暗门。",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.TimelineEvent(
                        "timeline_001",
                        1,
                        "chapter_1",
                        "深夜",
                        "林夏进入旧剧场。"
                )),
                List.of(new GlobalStoryState.Relationship(
                        "relationship_001",
                        "character_001",
                        "character_002",
                        "旧识",
                        "两人曾共同排练。",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.StoryProp(
                        "prop_001",
                        "铜钥匙",
                        "打开暗门的旧钥匙。",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.Foreshadowing(
                        "foreshadowing_001",
                        "道具箱上的刻痕重复出现。",
                        "可能指向失踪事件。",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.ConflictNote(
                        "conflict_001",
                        "人物身份可能存在同名混淆。",
                        List.of("character_002", "character_003"),
                        List.of("chapter_1")
                ))
        );
    }

    private ScenePlan scenePlan() {
        return new ScenePlan(List.of(new ScenePlan.Scene(
                "scene_001",
                "act_1",
                1,
                "chapter_1",
                "暗门前",
                "旧剧场后台",
                "深夜",
                List.of("character_001"),
                "建立主角目标与危险入口。",
                "是否独自进入暗门。",
                List.of(new ScenePlan.Beat(
                        "林夏摸到铜钥匙，停在暗门前。",
                        "她在灰尘里摸到一枚冰凉的铜钥匙。",
                        List.of("character_001")
                )),
                "林夏在旧剧场后台发现一扇暗门。"
        )));
    }

    private SourceEvidence evidence(String excerpt) {
        return new SourceEvidence("chapter_1", 1, excerpt);
    }
}
