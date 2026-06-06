package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.CinematicTreatment;
import com.hjan.actone.script.ai.model.DialogueDraft;
import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.ai.model.SourceTrace;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidationResult;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptAiPipelineServiceTest {

    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptYamlValidator yamlValidator = new ScriptYamlValidator();
    private final ScriptYamlDraftService ruleBasedDraftService = new ScriptYamlDraftService(chapterParser);

    @Test
    void generateReturnsValidatedAiYamlWhenAllStagesSucceed() {
        FakeStages stages = new FakeStages();
        stages.generatedYaml = validYaml("ai_pipeline");
        ScriptAiPipelineService service = service(stages);

        ScriptAiPipelineResult result = service.generate(request());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isFalse();
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.AI_PIPELINE);
        assertThat(result.yaml()).contains("generation_mode: \"ai_pipeline\"");
        assertThat(result.context().currentStage()).isEqualTo(PipelineStage.COMPLETED);
        assertThat(result.context().chapterSummaries()).hasSize(3);
        assertThat(stages.summaryCalls).isEqualTo(3);
        assertThat(stages.globalContext.chapterSummaries()).hasSize(3);
        assertThat(stages.sceneContext.globalStoryState()).isEqualTo(GlobalStoryState.empty());
        assertThat(stages.cinematicContext.scenePlan().scenes()).hasSize(1);
        assertThat(stages.yamlContext.cinematicTreatment().scenes()).hasSize(1);
        assertThat(stages.dialogueContext.cinematicTreatment().scenes()).hasSize(1);
        assertThat(stages.sourceTraceContext.dialogueDraft().scenes()).hasSize(1);
        assertThat(stages.yamlContext.sourceTrace().scenes()).hasSize(1);
        assertThat(stages.yamlContext.scenePlan().scenes()).hasSize(1);
        assertThat(stages.repairCalls).isZero();
    }

    @Test
    void generateNotifiesObserverOfStageProgressAndValidation() {
        FakeStages stages = new FakeStages();
        stages.generatedYaml = validYaml("ai_pipeline");
        ScriptAiPipelineService service = service(stages);
        List<String> events = new ArrayList<>();

        ScriptAiPipelineObserver observer = new ScriptAiPipelineObserver() {
            @Override
            public void onPipelineStart(PipelineContext context) {
                events.add("pipeline:" + context.currentStage());
            }

            @Override
            public void onStageStart(PipelineContext context, PipelineStage stage) {
                events.add("start:" + stage);
            }

            @Override
            public void onStageSuccess(PipelineContext context, PipelineStage stage) {
                events.add("success:" + stage);
            }

            @Override
            public void onValidation(PipelineContext context, ScriptYamlValidationResult validationResult) {
                events.add("validation:" + validationResult.status());
            }

            @Override
            public void onFallback(PipelineContext context, PipelineStage fallbackStage, String reason) {
                events.add("fallback:" + fallbackStage);
            }

            @Override
            public void onComplete(ScriptAiPipelineResult result) {
                events.add("complete:" + result.context().currentStage());
            }
        };

        ScriptAiPipelineResult result = service.generate(request(), observer);

        assertThat(result.valid()).isTrue();
        assertThat(events).containsSubsequence(
                "pipeline:INIT",
                "start:CHAPTER_SUMMARY",
                "success:CHAPTER_SUMMARY",
                "start:GLOBAL_STORY_EXTRACTION",
                "success:GLOBAL_STORY_EXTRACTION",
                "start:SCENE_PLANNING",
                "success:SCENE_PLANNING",
                "start:PSYCHOLOGICAL_VISUALIZATION",
                "success:PSYCHOLOGICAL_VISUALIZATION",
                "start:DIALOGUE_GENERATION",
                "success:DIALOGUE_GENERATION",
                "start:SOURCE_TRACE",
                "success:SOURCE_TRACE",
                "start:SCRIPT_YAML_GENERATION",
                "success:SCRIPT_YAML_GENERATION",
                "start:YAML_VALIDATION",
                "success:YAML_VALIDATION",
                "validation:valid",
                "complete:COMPLETED"
        );
        assertThat(events).doesNotContain("fallback:CHAPTER_SUMMARY");
    }

    @Test
    void generateStopsBeforeNextStageWhenObserverIsCanceled() {
        FakeStages stages = new FakeStages();
        stages.generatedYaml = validYaml("ai_pipeline");
        ScriptAiPipelineService service = service(stages);
        AtomicBoolean canceled = new AtomicBoolean(false);

        ScriptAiPipelineObserver observer = new ScriptAiPipelineObserver() {
            @Override
            public void onStageSuccess(PipelineContext context, PipelineStage stage) {
                if (stage == PipelineStage.CHAPTER_SUMMARY) {
                    canceled.set(true);
                }
            }

            @Override
            public boolean isCanceled() {
                return canceled.get();
            }
        };

        assertThatThrownBy(() -> service.generate(request(), observer))
                .isInstanceOf(ScriptAiPipelineCanceledException.class)
                .hasMessageContaining("GLOBAL_STORY_EXTRACTION");
        assertThat(stages.summaryCalls).isEqualTo(3);
        assertThat(stages.globalContext).isNull();
        assertThat(stages.repairCalls).isZero();
    }

    @Test
    void generateContinuesFromCachedScenePlanWhenResumeStateExists() {
        List<NovelChapter> chapters = chapterParser.parse(request().text());
        FakeStages cachedStages = new FakeStages();
        List<ChapterSummary> cachedSummaries = chapters.stream()
                .map(cachedStages::summarize)
                .toList();
        PipelineContext cachedContext = PipelineContext.create(
                        null,
                        "测试剧本",
                        "作者A",
                        "主角在旧剧场面对三次选择。",
                        chapters.size()
                )
                .withChapterSummaries(cachedSummaries)
                .withGlobalStoryState(GlobalStoryState.empty());
        ScenePlan cachedScenePlan = cachedStages.plan(cachedContext);

        FakeStages stages = new FakeStages();
        stages.generatedYaml = validYaml("ai_pipeline");
        ScriptAiPipelineService service = service(stages);

        ScriptAiPipelineResult result = service.generate(
                request(),
                ScriptAiPipelineObserver.NOOP,
                new ScriptAiPipelineResumeState(cachedSummaries, GlobalStoryState.empty(), cachedScenePlan)
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isFalse();
        assertThat(stages.summaryCalls).isZero();
        assertThat(stages.globalContext).isNull();
        assertThat(stages.sceneContext).isNull();
        assertThat(stages.cinematicContext.scenePlan()).isEqualTo(cachedScenePlan);
        assertThat(stages.cinematicContext.chapterSummaries()).hasSize(3);
    }

    @Test
    void generateRepairsInvalidAiYamlBeforeReturningResult() {
        FakeStages stages = new FakeStages();
        stages.generatedYaml = invalidYaml();
        stages.repairedYaml = validYaml("ai_pipeline");
        ScriptAiPipelineService service = service(stages);

        ScriptAiPipelineResult result = service.generate(request());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isFalse();
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.AI_PIPELINE);
        assertThat(result.yaml()).contains("id: \"scene_001\"");
        assertThat(result.context().repairedYaml()).contains("generation_mode: \"ai_pipeline\"");
        assertThat(stages.repairCalls).isEqualTo(1);
        assertThat(stages.repairContext.validationStatus()).isEqualTo("invalid");
    }

    @Test
    void generateFallsBackToRuleBasedYamlWhenAiStageFails() {
        FakeStages stages = new FakeStages();
        stages.summaryFailure = new IllegalStateException("provider returned api-key=secret and raw novel");
        ScriptAiPipelineService service = service(stages);

        ScriptAiPipelineResult result = service.generate(request());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isTrue();
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.RULE_BASED_FALLBACK);
        assertThat(result.fallbackReason())
                .contains("CHAPTER_SUMMARY")
                .doesNotContain("api-key", "secret", "raw novel");
        assertThat(result.yaml())
                .contains("generation_mode: \"rule_based_fallback\"")
                .doesNotContain("generation_mode: \"rule_based_scaffold\"");
        assertThat(stages.summaryCalls).isEqualTo(1);
        assertThat(stages.repairCalls).isZero();
    }

    @Test
    void generateFallsBackWhenRepairStillInvalid() {
        FakeStages stages = new FakeStages();
        stages.generatedYaml = invalidYaml();
        stages.repairedYaml = invalidYaml();
        ScriptAiPipelineService service = service(stages);

        ScriptAiPipelineResult result = service.generate(request());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isTrue();
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.RULE_BASED_FALLBACK);
        assertThat(result.fallbackReason()).contains("自动修复未通过");
        assertThat(result.yaml()).contains("generation_mode: \"rule_based_fallback\"");
        assertThat(stages.repairCalls).isEqualTo(1);
    }

    @Test
    void generateRejectsInputWithLessThanThreeChapters() {
        ScriptAiPipelineService service = service(new FakeStages());

        assertThatThrownBy(() -> service.generate(new ScriptYamlGenerateRequest(null, null, null, """
                第 1 章
                内容

                第 2 章
                内容
                """)))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("至少需要 3 个章节");
    }

    private ScriptAiPipelineService service(FakeStages stages) {
        return new ScriptAiPipelineService(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                stages,
                stages,
                stages,
                stages,
                stages,
                stages,
                stages,
                stages
        );
    }

    private ScriptYamlGenerateRequest request() {
        return new ScriptYamlGenerateRequest(
                "测试剧本",
                "作者A",
                "主角在旧剧场面对三次选择。",
                """
                        第 1 章 开端
                        林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。

                        第 2 章 发展
                        她按照信里的线索找到后台暗门，却被多年未见的朋友拦住。

                        第 3 章 选择
                        演出开始前，林夏必须决定公开真相，还是保护朋友的秘密。
                        """
        );
    }

    private String validYaml(String generationMode) {
        return """
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "测试剧本"
                  author: "作者A"
                  logline: "主角在旧剧场面对三次选择。"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "%s"
                characters: []
                acts:
                  - id: "act_1"
                    title: "第一幕"
                    chapter_range:
                      start: 1
                      end: 3
                    dramatic_function: "建立目标与选择"
                scenes:
                  - id: "scene_001"
                    act_id: "act_1"
                    chapter_index: 1
                    source_chapter_id: "chapter_1"
                    title: "旧剧场"
                    location: "旧剧场"
                    time: "深夜"
                    scene_purpose: "建立主角目标"
                    action: "林夏推开旧剧场的门。"
                    dialogues: []
                    source_excerpt: "林夏推开旧剧场的门。"
                """.formatted(generationMode);
    }

    private String invalidYaml() {
        return """
                schema_version: "1.0"
                kind: "screenplay_draft"
                metadata:
                  title: "测试剧本"
                source:
                  type: "novel"
                  chapter_count: 3
                  generation_mode: "ai_pipeline"
                characters: []
                acts: []
                scenes: []
                """;
    }

    private static class FakeStages implements ChapterSummaryStage,
            GlobalStoryExtractionStage,
            ScenePlanningStage,
            CinematicTreatmentStage,
            DialogueGenerationStage,
            SourceTraceStage,
            ScriptYamlGenerationStage,
            YamlRepairStage {

        private final SourceTraceStage sourceTraceStage = new DeterministicSourceTraceStage();
        private int summaryCalls;
        private int repairCalls;
        private RuntimeException summaryFailure;
        private String generatedYaml;
        private String repairedYaml;
        private PipelineContext globalContext;
        private PipelineContext sceneContext;
        private PipelineContext cinematicContext;
        private PipelineContext dialogueContext;
        private PipelineContext sourceTraceContext;
        private PipelineContext yamlContext;
        private PipelineContext repairContext;

        @Override
        public ChapterSummary summarize(NovelChapter chapter) {
            summaryCalls++;
            if (summaryFailure != null) {
                throw summaryFailure;
            }
            SourceEvidence evidence = new SourceEvidence(
                    chapter.id(),
                    chapter.index(),
                    chapter.content().substring(0, Math.min(20, chapter.content().length()))
            );
            return new ChapterSummary(
                    chapter.id(),
                    chapter.index(),
                    chapter.title(),
                    "章节摘要 " + chapter.index(),
                    List.of(new ChapterSummary.KeyEvent(
                            "event_%03d".formatted(chapter.index()),
                            "关键事件 " + chapter.index(),
                            List.of(evidence)
                    )),
                    List.of("林夏"),
                    List.of("旧剧场"),
                    List.of("深夜"),
                    List.of("选择冲突"),
                    List.of(),
                    List.of(evidence)
            );
        }

        @Override
        public GlobalStoryState extract(PipelineContext context) {
            globalContext = context;
            return GlobalStoryState.empty();
        }

        @Override
        public ScenePlan plan(PipelineContext context) {
            sceneContext = context;
            return new ScenePlan(List.of(new ScenePlan.Scene(
                    "scene_001",
                    "act_1",
                    1,
                    "chapter_1",
                    "旧剧场",
                    "旧剧场",
                    "深夜",
                    List.of("林夏"),
                    "建立主角目标",
                    "是否进入旧剧场",
                    List.of(new ScenePlan.Beat(
                            "林夏推门进入。",
                            "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                            List.of("character_001")
                    )),
                    "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。"
            )));
        }

        @Override
        public CinematicTreatment visualize(PipelineContext context) {
            cinematicContext = context;
            return new CinematicTreatment(List.of(new CinematicTreatment.SceneTreatment(
                    "scene_001",
                    1,
                    "chapter_1",
                    "林夏推开门后停住，手指压皮信封，视线越过空荡舞台。",
                    List.of(new CinematicTreatment.Transformation(
                            "她害怕却不愿退缩。",
                            "林夏停在门口，指尖压皮信封，随后迈进黑暗的观众席。",
                            "动作与停顿",
                            "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                            List.of("character_001")
                    ))
            )));
        }

        @Override
        public DialogueDraft draft(PipelineContext context) {
            dialogueContext = context;
            return new DialogueDraft(List.of(new DialogueDraft.SceneDialogues(
                    "scene_001",
                    1,
                    "chapter_1",
                    List.of(new DialogueDraft.DialogueLine(
                            "林夏",
                            "character_001",
                            "我进去。你在这里等我。",
                            "克制",
                            "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。"
                    ))
            )));
        }

        @Override
        public SourceTrace trace(PipelineContext context, List<NovelChapter> chapters) {
            sourceTraceContext = context;
            return sourceTraceStage.trace(context, chapters);
        }

        @Override
        public String generate(PipelineContext context) {
            yamlContext = context;
            return generatedYaml;
        }

        @Override
        public String repair(PipelineContext context, ScriptYamlValidationResult validationResult) {
            repairCalls++;
            repairContext = context;
            return repairedYaml;
        }
    }
}
