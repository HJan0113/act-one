package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.DialogueDraft;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.ai.model.SourceTrace;
import com.hjan.actone.script.model.NovelChapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicSourceTraceStageTest {

    private final DeterministicSourceTraceStage stage = new DeterministicSourceTraceStage();

    @Test
    void traceCollectsSceneEvidenceAndDeduplicatesExcerpts() {
        SourceTrace trace = stage.trace(context(), chapters());

        assertThat(trace.scenes()).singleElement()
                .satisfies(scene -> {
                    assertThat(scene.sceneId()).isEqualTo("scene_001");
                    assertThat(scene.sourceChapterId()).isEqualTo("chapter_1");
                    assertThat(scene.chapterIndex()).isEqualTo(1);
                    assertThat(scene.sourceExcerpt()).isEqualTo("林夏在旧剧场后台发现一扇暗门。");
                    assertThat(scene.evidence()).extracting(SourceEvidence::excerpt)
                            .containsExactly(
                                    "林夏在旧剧场后台发现一扇暗门。",
                                    "她在灰尘里摸到一枚冰凉的铜钥匙。"
                            );
                });
    }

    @Test
    void traceRejectsExcerptThatCannotBeFoundInSourceChapter() {
        PipelineContext context = context().withScenePlan(new ScenePlan(List.of(new ScenePlan.Scene(
                "scene_001",
                "act_1",
                1,
                "chapter_1",
                "暗门前",
                "旧剧场后台",
                "深夜",
                List.of("character_001"),
                "建立主角目标。",
                "是否进入暗门。",
                List.of(new ScenePlan.Beat(
                        "林夏停在暗门前。",
                        "这段摘录并不存在于原文。",
                        List.of("character_001")
                )),
                "林夏在旧剧场后台发现一扇暗门。"
        ))));

        assertThatThrownBy(() -> stage.trace(context, chapters()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("源文摘录无法回查原文");
    }

    @Test
    void traceRejectsUnknownSourceChapter() {
        PipelineContext context = context().withScenePlan(new ScenePlan(List.of(new ScenePlan.Scene(
                "scene_001",
                "act_1",
                1,
                "chapter_9",
                "暗门前",
                "旧剧场后台",
                "深夜",
                List.of("character_001"),
                "建立主角目标。",
                "是否进入暗门。",
                List.of(new ScenePlan.Beat(
                        "林夏停在暗门前。",
                        "林夏在旧剧场后台发现一扇暗门。",
                        List.of("character_001")
                )),
                "林夏在旧剧场后台发现一扇暗门。"
        ))));

        assertThatThrownBy(() -> stage.trace(context, chapters()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("源文追溯章节引用非法");
    }

    @Test
    void traceRejectsIntermediateSourceMismatch() {
        PipelineContext context = context().withDialogueDraft(new DialogueDraft(List.of(new DialogueDraft.SceneDialogues(
                "scene_001",
                2,
                "chapter_2",
                List.of(new DialogueDraft.DialogueLine(
                        "林夏",
                        "character_001",
                        "我进去。",
                        "克制",
                        "林夏在旧剧场后台发现一扇暗门。"
                ))
        ))));

        assertThatThrownBy(() -> stage.trace(context, chapters()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("源文追溯场景来源不一致");
    }

    @Test
    void traceRejectsContextWithoutRequiredInputs() {
        PipelineContext context = PipelineContext.create("project_1", "暗门", null, null, 3);

        assertThatThrownBy(() -> stage.trace(context, chapters()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scenePlan 不能为空");
    }

    private PipelineContext context() {
        return PipelineContext.create("project_1", "暗门", "测试作者", "林夏追查旧剧场秘密。", 3)
                .withGlobalStoryState(GlobalStoryState.empty())
                .withScenePlan(scenePlan())
                .withCinematicTreatment(cinematicTreatment())
                .withDialogueDraft(dialogueDraft());
    }

    private List<NovelChapter> chapters() {
        return List.of(
                new NovelChapter(
                        "chapter_1",
                        1,
                        "第一章 暗门",
                        "林夏在旧剧场后台发现一扇暗门。她在灰尘里摸到一枚冰凉的铜钥匙。",
                        36
                ),
                new NovelChapter("chapter_2", 2, "第二章 旧友", "旧友在后台出现。", 8),
                new NovelChapter("chapter_3", 3, "第三章 选择", "演出开始前，林夏做出选择。", 14)
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
                "建立主角目标。",
                "是否进入暗门。",
                List.of(new ScenePlan.Beat(
                        "林夏摸到铜钥匙。",
                        "她在灰尘里摸到一枚冰凉的铜钥匙。",
                        List.of("character_001")
                )),
                "林夏在旧剧场后台发现一扇暗门。"
        )));
    }

    private CinematicTreatment cinematicTreatment() {
        return new CinematicTreatment(List.of(new CinematicTreatment.SceneTreatment(
                "scene_001",
                1,
                "chapter_1",
                "林夏摸到铜钥匙，停在暗门前。",
                List.of(new CinematicTreatment.Transformation(
                        "她害怕却不愿退缩。",
                        "林夏停在暗门前，指尖捏紧铜钥匙。",
                        "动作与道具",
                        "她在灰尘里摸到一枚冰凉的铜钥匙。",
                        List.of("character_001")
                ))
        )));
    }

    private DialogueDraft dialogueDraft() {
        return new DialogueDraft(List.of(new DialogueDraft.SceneDialogues(
                "scene_001",
                1,
                "chapter_1",
                List.of(new DialogueDraft.DialogueLine(
                        "林夏",
                        "character_001",
                        "我进去。",
                        "克制",
                        "林夏在旧剧场后台发现一扇暗门。"
                ))
        )));
    }
}
