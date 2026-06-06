package com.hjan.actone.script.ai.support;

import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.DialogueDraft;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.ai.pipeline.CinematicTreatmentStage;
import com.hjan.actone.script.ai.pipeline.ChapterSummaryStage;
import com.hjan.actone.script.ai.pipeline.DialogueGenerationStage;
import com.hjan.actone.script.ai.pipeline.GlobalStoryExtractionStage;
import com.hjan.actone.script.ai.pipeline.ScenePlanningStage;
import com.hjan.actone.script.ai.pipeline.ScriptYamlGenerationStage;
import com.hjan.actone.script.ai.pipeline.YamlRepairStage;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.service.ScriptYamlValidationResult;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FakeScriptAiStages implements ChapterSummaryStage,
        GlobalStoryExtractionStage,
        ScenePlanningStage,
        CinematicTreatmentStage,
        DialogueGenerationStage,
        ScriptYamlGenerationStage,
        YamlRepairStage {

    private final Map<PipelineStage, RuntimeException> failures = new EnumMap<>(PipelineStage.class);
    private String generatedYaml = ScriptAiTestFixtures.validYaml();
    private String repairedYaml = ScriptAiTestFixtures.validYaml();
    private int summaryCalls;
    private int repairCalls;

    public FakeScriptAiStages generatedYaml(String generatedYaml) {
        this.generatedYaml = generatedYaml;
        return this;
    }

    public FakeScriptAiStages repairedYaml(String repairedYaml) {
        this.repairedYaml = repairedYaml;
        return this;
    }

    public FakeScriptAiStages failingAt(PipelineStage stage, RuntimeException failure) {
        failures.put(stage, failure);
        return this;
    }

    public int summaryCalls() {
        return summaryCalls;
    }

    public int repairCalls() {
        return repairCalls;
    }

    @Override
    public ChapterSummary summarize(NovelChapter chapter) {
        summaryCalls++;
        failIfConfigured(PipelineStage.CHAPTER_SUMMARY);
        SourceEvidence evidence = evidence(chapter);
        return new ChapterSummary(
                chapter.id(),
                chapter.index(),
                chapter.title(),
                "林夜在破庙中遭遇任务反转。",
                List.of(new ChapterSummary.KeyEvent(
                        "event_%03d".formatted(chapter.index()),
                        "第 " + chapter.index() + " 章关键事件",
                        List.of(evidence)
                )),
                List.of("林夜"),
                List.of("荒山破庙"),
                List.of("雨夜子时"),
                List.of("刺杀任务与保护弱者之间的选择"),
                List.of(new ChapterSummary.InnerState(
                        "林夜",
                        "他从冷漠执行者转向主动选择。",
                        evidence
                )),
                List.of(evidence)
        );
    }

    @Override
    public GlobalStoryState extract(PipelineContext context) {
        failIfConfigured(PipelineStage.GLOBAL_STORY_EXTRACTION);
        return new GlobalStoryState(
                List.of(new GlobalStoryState.CharacterProfile(
                        "character_001",
                        "林夜",
                        List.of(),
                        "主角",
                        "江湖暗刃，临阵改换雇主。",
                        "chapter_1",
                        List.of("chapter_1", "chapter_2", "chapter_3")
                )),
                List.of(new GlobalStoryState.LocationProfile(
                        "location_001",
                        "荒山破庙",
                        "雨夜中的刺杀地点。",
                        List.of("chapter_1", "chapter_2", "chapter_3")
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    @Override
    public ScenePlan plan(PipelineContext context) {
        failIfConfigured(PipelineStage.SCENE_PLANNING);
        return new ScenePlan(List.of(new ScenePlan.Scene(
                "scene_001",
                "act_1",
                1,
                "chapter_1",
                "破庙伏杀",
                "荒山破庙",
                "雨夜子时",
                List.of("character_001"),
                "建立林夜的任务与危险氛围。",
                "刺杀任务与真实目标不一致。",
                List.of(new ScenePlan.Beat(
                        "林夜吹熄残烛，隐入阴影。",
                        "他吹熄了身旁仅存的半截残烛。",
                        List.of("character_001")
                )),
                "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
        )));
    }

    @Override
    public CinematicTreatment visualize(PipelineContext context) {
        failIfConfigured(PipelineStage.PSYCHOLOGICAL_VISUALIZATION);
        return new CinematicTreatment(List.of(new CinematicTreatment.SceneTreatment(
                "scene_001",
                1,
                "chapter_1",
                "林夜吹熄残烛，指节扣紧刀柄；哭声传来时，他停住脚步，刀尖慢慢垂下。",
                List.of(new CinematicTreatment.Transformation(
                        "他从冷漠执行者转向主动选择。",
                        "哭声传来，林夜扣紧刀柄的手停在半空，刀尖慢慢垂下。",
                        "动作与停顿",
                        "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。",
                        List.of("character_001")
                ))
        )));
    }

    @Override
    public DialogueDraft draft(PipelineContext context) {
        failIfConfigured(PipelineStage.DIALOGUE_GENERATION);
        return new DialogueDraft(List.of(new DialogueDraft.SceneDialogues(
                "scene_001",
                1,
                "chapter_1",
                List.of(new DialogueDraft.DialogueLine(
                        "林夜",
                        "character_001",
                        "别出声。跟紧我。",
                        "压低声音",
                        "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
                ))
        )));
    }

    @Override
    public String generate(PipelineContext context) {
        failIfConfigured(PipelineStage.SCRIPT_YAML_GENERATION);
        return generatedYaml;
    }

    @Override
    public String repair(PipelineContext context, ScriptYamlValidationResult validationResult) {
        repairCalls++;
        failIfConfigured(PipelineStage.YAML_REPAIR);
        return repairedYaml;
    }

    private SourceEvidence evidence(NovelChapter chapter) {
        return new SourceEvidence(
                chapter.id(),
                chapter.index(),
                chapter.content().substring(0, Math.min(36, chapter.content().length()))
        );
    }

    private void failIfConfigured(PipelineStage stage) {
        RuntimeException failure = failures.get(stage);
        if (failure != null) {
            throw failure;
        }
    }
}
