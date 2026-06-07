package com.hjan.actone.script.ai.support;

import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.ai.pipeline.ChapterSummaryStage;
import com.hjan.actone.script.ai.pipeline.ScenePlanningStage;
import com.hjan.actone.script.ai.pipeline.ScriptYamlGenerationStage;
import com.hjan.actone.script.model.NovelChapter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FakeScriptAiStages implements ChapterSummaryStage, ScenePlanningStage, ScriptYamlGenerationStage {

    private final Map<PipelineStage, RuntimeException> failures = new EnumMap<>(PipelineStage.class);
    private String generatedYaml = ScriptAiTestFixtures.validYaml();
    private int summaryCalls;
    private PipelineContext scenePlanningContext;
    private PipelineContext yamlGenerationContext;

    public FakeScriptAiStages generatedYaml(String generatedYaml) {
        this.generatedYaml = generatedYaml;
        return this;
    }

    public FakeScriptAiStages failingAt(PipelineStage stage, RuntimeException failure) {
        failures.put(stage, failure);
        return this;
    }

    public int summaryCalls() {
        return summaryCalls;
    }

    public PipelineContext scenePlanningContext() {
        return scenePlanningContext;
    }

    public PipelineContext yamlGenerationContext() {
        return yamlGenerationContext;
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
                List.of("林夜", "孩子"),
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
    public ScenePlan plan(PipelineContext context) {
        failIfConfigured(PipelineStage.SCENE_PLANNING);
        scenePlanningContext = context;
        return new ScenePlan(List.of(
                new ScenePlan.Scene(
                        "scene_001",
                        "act_1",
                        1,
                        "chapter_1",
                        "破庙伏杀",
                        "荒山破庙",
                        "雨夜子时",
                        List.of("林夜", "孩子"),
                        "建立林夜的任务与危险氛围。",
                        "刺杀任务与真实目标不一致。",
                        List.of(new ScenePlan.Beat(
                                "林夜吹熄残烛，隐入阴影。",
                                "他吹熄了身旁仅存的半截残烛，将自己彻底融入深邃的阴影之中。",
                                List.of("林夜")
                        )),
                        "林夜缓缓睁开眼，瞳孔在黑暗中收缩如针。"
                ),
                new ScenePlan.Scene(
                        "scene_002",
                        "act_1",
                        1,
                        "chapter_1",
                        "刀锋出鞘",
                        "荒山破庙",
                        "雨夜子时",
                        List.of("林夜"),
                        "把同一章节拆成第二个可拍场景，推进刺杀即将发生。",
                        "林夜是否继续执行旧任务。",
                        List.of(new ScenePlan.Beat(
                                "刀锋在黑暗中亮起。",
                                "刀锋出鞘半寸，清冷的寒光映亮了他毫无波澜的侧脸。",
                                List.of("林夜")
                        )),
                        "猎物，终于来了。"
                )
        ));
    }

    @Override
    public String generate(PipelineContext context) {
        failIfConfigured(PipelineStage.SCRIPT_YAML_GENERATION);
        yamlGenerationContext = context;
        return generatedYaml;
    }

    private void failIfConfigured(PipelineStage stage) {
        RuntimeException failure = failures.get(stage);
        if (failure != null) {
            throw failure;
        }
    }

    private SourceEvidence evidence(NovelChapter chapter) {
        return new SourceEvidence(
                chapter.id(),
                chapter.index(),
                chapter.content().substring(0, Math.min(40, chapter.content().length()))
        );
    }
}
