package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.DialogueDraft;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineConsistencyReport;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.ai.model.SourceTrace;
import com.hjan.actone.script.model.NovelChapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineConsistencyCheckerTest {

    private final PipelineConsistencyChecker checker = new PipelineConsistencyChecker();

    @Test
    void checkReturnsValidReportForConsistentPipelineContext() {
        PipelineConsistencyReport report = checker.check(context(), chapters());

        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
        assertThat(report.summary()).isEqualTo("一致性校验通过");
    }

    @Test
    void checkReportsUnknownSceneCharacterAndSourceTraceMismatch() {
        PipelineContext brokenContext = context()
                .withScenePlan(new ScenePlan(List.of(new ScenePlan.Scene(
                        "scene_001",
                        "act_1",
                        1,
                        "chapter_1",
                        "暗门前",
                        "旧剧场后台",
                        "深夜",
                        List.of("character_999"),
                        "建立目标。",
                        "是否进入暗门。",
                        List.of(new ScenePlan.Beat(
                                "林夏停在暗门前。",
                                "林夏在旧剧场后台发现一扇暗门。",
                                List.of("character_999")
                        )),
                        "林夏在旧剧场后台发现一扇暗门。"
                ))))
                .withSourceTrace(new SourceTrace(List.of(new SourceTrace.SceneTrace(
                        "scene_001",
                        1,
                        "chapter_1",
                        "林夏在旧剧场后台发现一扇暗门。",
                        List.of(new SourceEvidence(
                                "chapter_2",
                                2,
                                "旧友在后台出现。"
                        ))
                ))));

        PipelineConsistencyReport report = checker.check(brokenContext, chapters());

        assertThat(report.valid()).isFalse();
        assertThat(report.errors())
                .extracting(PipelineConsistencyReport.Issue::code)
                .contains(
                        "scene.character.unknown",
                        "scene.beat.character.unknown",
                        "sourceTrace.evidence.source.mismatch"
                );
        assertThat(report.summary()).contains("人物引用不存在：character_999");
    }

    @Test
    void validateThrowsWhenReportHasErrors() {
        PipelineContext brokenContext = context()
                .withScenePlan(new ScenePlan(List.of(new ScenePlan.Scene(
                        "scene_009",
                        "act_x",
                        2,
                        "chapter_1",
                        "暗门前",
                        "旧剧场后台",
                        "深夜",
                        List.of("character_001"),
                        "建立目标。",
                        "是否进入暗门。",
                        List.of(new ScenePlan.Beat(
                                "林夏停在暗门前。",
                                "林夏在旧剧场后台发现一扇暗门。",
                                List.of("character_001")
                        )),
                        "林夏在旧剧场后台发现一扇暗门。"
                ))));

        assertThatThrownBy(() -> checker.validate(brokenContext, chapters()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI Pipeline 一致性校验失败")
                .hasMessageContaining("场景 ID 未按顺序稳定编号")
                .hasMessageContaining("场景 act_id 格式非法");
    }

    private PipelineContext context() {
        return PipelineContext.create("project_1", "暗门", "测试作者", "林夏追查旧剧场秘密。", 3)
                .withGlobalStoryState(globalStoryState())
                .withScenePlan(scenePlan())
                .withCinematicTreatment(cinematicTreatment())
                .withDialogueDraft(dialogueDraft())
                .withSourceTrace(sourceTrace());
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

    private GlobalStoryState globalStoryState() {
        return new GlobalStoryState(
                List.of(new GlobalStoryState.CharacterProfile(
                        "character_001",
                        "林夏",
                        List.of(),
                        "主角",
                        "追查旧剧场秘密的调查者。",
                        "chapter_1",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.LocationProfile(
                        "location_001",
                        "旧剧场后台",
                        "暗门所在地点。",
                        List.of("chapter_1")
                )),
                List.of(new GlobalStoryState.TimelineEvent(
                        "timeline_001",
                        1,
                        "chapter_1",
                        "深夜",
                        "林夏发现旧剧场暗门。"
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of()
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
                "建立目标。",
                "是否进入暗门。",
                List.of(new ScenePlan.Beat(
                        "林夏停在暗门前。",
                        "林夏在旧剧场后台发现一扇暗门。",
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

    private SourceTrace sourceTrace() {
        return new SourceTrace(List.of(new SourceTrace.SceneTrace(
                "scene_001",
                1,
                "chapter_1",
                "林夏在旧剧场后台发现一扇暗门。",
                List.of(
                        new SourceEvidence("chapter_1", 1, "林夏在旧剧场后台发现一扇暗门。"),
                        new SourceEvidence("chapter_1", 1, "她在灰尘里摸到一枚冰凉的铜钥匙。")
                )
        )));
    }
}
