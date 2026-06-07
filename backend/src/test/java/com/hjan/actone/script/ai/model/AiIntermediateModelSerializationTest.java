package com.hjan.actone.script.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiIntermediateModelSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chapterSummaryRoundTripsJsonAndKeepsSourceEvidence() throws Exception {
        ChapterSummary summary = chapterSummary("chapter_1", 1);

        String json = objectMapper.writeValueAsString(summary);
        ChapterSummary restored = objectMapper.readValue(json, ChapterSummary.class);

        assertThat(restored.chapterId()).isEqualTo("chapter_1");
        assertThat(restored.keyEvents()).singleElement()
                .satisfies(event -> assertThat(event.evidence())
                        .extracting(SourceEvidence::sourceChapterId)
                        .containsExactly("chapter_1"));
        assertThat(restored.innerStates()).singleElement()
                .satisfies(state -> assertThat(state.evidence().excerpt()).contains("旧剧场"));
    }

    @Test
    void scenePlanRoundTripsJsonAndAllowsCharacterNames() throws Exception {
        ScenePlan plan = scenePlan();

        String json = objectMapper.writeValueAsString(plan);
        ScenePlan restored = objectMapper.readValue(json, ScenePlan.class);

        assertThat(restored.scenes()).hasSize(2);
        assertThat(restored.scenes()).extracting(ScenePlan.Scene::id)
                .containsExactly("scene_001", "scene_002");
        assertThat(restored.scenes().get(0).characters()).containsExactly("林夏", "旧友");
        assertThat(restored.scenes().get(0).beats()).singleElement()
                .satisfies(beat -> assertThat(beat.characterIds()).containsExactly("林夏"));
    }

    @Test
    void pipelineContextRoundTripsReducedState() throws Exception {
        PipelineContext context = PipelineContext.create("project_1", "暗门", "测试作者", "追查旧剧场秘密", 3)
                .withChapterSummaries(List.of(chapterSummary("chapter_1", 1)))
                .withScenePlan(scenePlan())
                .withGeneratedYaml("schema_version: \"1.0\"")
                .withValidation("valid", null)
                .completed();

        String json = objectMapper.writeValueAsString(context);
        PipelineContext restored = objectMapper.readValue(json, PipelineContext.class);

        assertThat(restored.currentStage()).isEqualTo(PipelineStage.COMPLETED);
        assertThat(restored.chapterSummaries()).hasSize(1);
        assertThat(restored.scenePlan().scenes()).hasSize(2);
        assertThat(restored.generatedYaml()).contains("schema_version");
        assertThat(restored.validationStatus()).isEqualTo("valid");
    }

    @Test
    void pipelineContextDefaultsToInitAndEmptySummaries() {
        PipelineContext context = PipelineContext.create(null, "暗门", null, null, 3);

        assertThat(context.taskId()).startsWith("task_");
        assertThat(context.currentStage()).isEqualTo(PipelineStage.INIT);
        assertThat(context.chapterSummaries()).isEmpty();
        assertThat(context.scenePlan()).isNull();
    }

    @Test
    void validatesRequiredIntermediateFields() {
        assertThatThrownBy(() -> new SourceEvidence("chapter_1", 0, "摘录"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chapterIndex");
        assertThatThrownBy(() -> new ChapterSummary(
                "",
                1,
                "第一章",
                "摘要",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SourceEvidence("chapter_1", 1, "摘录"))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chapterId");
        assertThatThrownBy(() -> new ScenePlan(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scenes");
    }

    private ChapterSummary chapterSummary(String chapterId, int chapterIndex) {
        SourceEvidence evidence = new SourceEvidence(chapterId, chapterIndex, "林夏在旧剧场后台发现一扇暗门。");
        return new ChapterSummary(
                chapterId,
                chapterIndex,
                "第 " + chapterIndex + " 章",
                "林夏在旧剧场发现暗门，并意识到匿名信指向多年前的秘密。",
                List.of(new ChapterSummary.KeyEvent(
                        "event_%03d".formatted(chapterIndex),
                        "林夏发现暗门",
                        List.of(evidence)
                )),
                List.of("林夏", "旧友"),
                List.of("旧剧场"),
                List.of("深夜"),
                List.of("公开真相与保护旧友"),
                List.of(new ChapterSummary.InnerState("林夏", "她害怕却不愿退缩。", evidence)),
                List.of(evidence)
        );
    }

    private ScenePlan scenePlan() {
        return new ScenePlan(List.of(
                new ScenePlan.Scene(
                        "scene_001",
                        "act_1",
                        1,
                        "chapter_1",
                        "旧剧场入口",
                        "旧剧场",
                        "深夜",
                        List.of("林夏", "旧友"),
                        "建立调查目标",
                        "是否进入旧剧场",
                        List.of(new ScenePlan.Beat(
                                "林夏推门进入。",
                                "林夏推开旧剧场的门。",
                                List.of("林夏")
                        )),
                        "林夏推开旧剧场的门。"
                ),
                new ScenePlan.Scene(
                        "scene_002",
                        "act_1",
                        2,
                        "chapter_2",
                        "后台暗门",
                        "旧剧场后台",
                        "深夜",
                        List.of("林夏", "旧友"),
                        "推进秘密线索",
                        "旧友阻拦林夏",
                        List.of(new ScenePlan.Beat(
                                "旧友挡在暗门前。",
                                "她找到后台暗门，却被多年未见的朋友拦住。",
                                List.of("旧友", "林夏")
                        )),
                        "她找到后台暗门，却被多年未见的朋友拦住。"
                )
        ));
    }
}
