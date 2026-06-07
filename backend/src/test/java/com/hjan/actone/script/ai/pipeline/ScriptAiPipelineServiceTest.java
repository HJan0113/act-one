package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.ai.model.ChapterSummary;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidationResult;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptAiPipelineServiceTest {

    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptYamlValidator yamlValidator = new ScriptYamlValidator();
    private final ScriptYamlDraftService ruleBasedDraftService = new ScriptYamlDraftService(chapterParser);

    @Test
    void generateReturnsValidatedAiYamlWhenReducedStagesSucceed() {
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
        assertThat(result.context().scenePlan().scenes()).hasSize(3);
        assertThat(stages.summaryCalls).isEqualTo(3);
        assertThat(stages.sceneContext.chapterSummaries()).hasSize(3);
        assertThat(stages.yamlContext.chapterSummaries()).hasSize(3);
        assertThat(stages.yamlContext.scenePlan().scenes()).hasSize(3);
    }

    @Test
    void generateNotifiesObserverOfReducedStageProgressAndValidation() {
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
                "start:SCENE_PLANNING",
                "success:SCENE_PLANNING",
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
                .hasMessageContaining("SCENE_PLANNING");
        assertThat(stages.summaryCalls).isEqualTo(3);
        assertThat(stages.sceneContext).isNull();
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
                .withChapterSummaries(cachedSummaries);
        ScenePlan cachedScenePlan = cachedStages.plan(cachedContext);

        FakeStages stages = new FakeStages();
        stages.generatedYaml = validYaml("ai_pipeline");
        ScriptAiPipelineService service = service(stages);

        ScriptAiPipelineResult result = service.generate(
                request(),
                ScriptAiPipelineObserver.NOOP,
                new ScriptAiPipelineResumeState(cachedSummaries, cachedScenePlan)
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isFalse();
        assertThat(stages.summaryCalls).isZero();
        assertThat(stages.sceneContext).isNull();
        assertThat(stages.yamlContext.scenePlan()).isEqualTo(cachedScenePlan);
        assertThat(stages.yamlContext.chapterSummaries()).hasSize(3);
    }

    @Test
    void generateFallsBackWhenAiYamlFailsSchemaValidationWithoutRepair() {
        FakeStages stages = new FakeStages();
        stages.generatedYaml = invalidYaml();
        ScriptAiPipelineService service = service(stages);

        ScriptAiPipelineResult result = service.generate(request());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isTrue();
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.RULE_BASED_FALLBACK);
        assertThat(result.fallbackReason()).contains("YAML Schema 校验失败");
        assertThat(result.yaml()).contains("generation_mode: \"rule_based_fallback\"");
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
    }

    @Test
    void generateSchedulesChapterSummariesOnConfiguredExecutor() {
        FakeStages stages = new FakeStages();
        stages.generatedYaml = validYaml("ai_pipeline");
        CountingExecutor executor = new CountingExecutor();
        ScriptAiPipelineService service = service(stages, ScriptAiPipelineOptions.defaults(), executor);

        ScriptAiPipelineResult result = service.generate(request());

        assertThat(result.valid()).isTrue();
        assertThat(executor.executeCalls()).isEqualTo(3);
    }

    @Test
    void generateRetriesStageAndReusesSuccessfulChapterSummaries() {
        FakeStages stages = new FakeStages();
        stages.generatedYaml = validYaml("ai_pipeline");
        stages.summaryFailureChapterIndex = 2;
        stages.summaryFailuresRemaining = 1;
        ScriptAiPipelineService service = service(stages, new ScriptAiPipelineOptions(1));

        ScriptAiPipelineResult result = service.generate(request());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isFalse();
        assertThat(stages.summaryCalls).isEqualTo(4);
        assertThat(stages.chapterOneCalls).isEqualTo(1);
        assertThat(stages.chapterTwoCalls).isEqualTo(2);
    }

    @Test
    void generateFallsBackAfterConfiguredStageRetriesAreExhausted() {
        FakeStages stages = new FakeStages();
        stages.summaryFailure = new IllegalStateException("provider timeout");
        ScriptAiPipelineService service = service(stages, new ScriptAiPipelineOptions(1));

        ScriptAiPipelineResult result = service.generate(request());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).contains("CHAPTER_SUMMARY");
        assertThat(stages.summaryCalls).isEqualTo(6);
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
        return service(stages, ScriptAiPipelineOptions.defaults());
    }

    private ScriptAiPipelineService service(FakeStages stages, ScriptAiPipelineOptions options) {
        return service(stages, options, Runnable::run);
    }

    private ScriptAiPipelineService service(FakeStages stages, ScriptAiPipelineOptions options, Executor executor) {
        return new ScriptAiPipelineService(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                stages,
                stages,
                stages,
                options,
                executor
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
                characters:
                  - id: "character_001"
                    name: "林夏"
                    role: "主角"
                    description: "旧剧场匿名信事件的调查者。"
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
                    ai_expansion: false
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

    private static class FakeStages implements ChapterSummaryStage, ScenePlanningStage, ScriptYamlGenerationStage {

        private int summaryCalls;
        private int chapterOneCalls;
        private int chapterTwoCalls;
        private RuntimeException summaryFailure;
        private int summaryFailureChapterIndex;
        private int summaryFailuresRemaining;
        private String generatedYaml;
        private PipelineContext sceneContext;
        private PipelineContext yamlContext;

        @Override
        public ChapterSummary summarize(NovelChapter chapter) {
            summaryCalls++;
            if (chapter.index() == 1) {
                chapterOneCalls++;
            }
            if (chapter.index() == 2) {
                chapterTwoCalls++;
            }
            if (summaryFailure != null) {
                throw summaryFailure;
            }
            if (summaryFailureChapterIndex == chapter.index() && summaryFailuresRemaining > 0) {
                summaryFailuresRemaining--;
                throw new IllegalStateException("provider timeout");
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
        public ScenePlan plan(PipelineContext context) {
            sceneContext = context;
            return new ScenePlan(List.of(
                    scene("scene_001", 1, "chapter_1", "旧剧场"),
                    scene("scene_002", 2, "chapter_2", "后台暗门"),
                    scene("scene_003", 3, "chapter_3", "演出前")
            ));
        }

        @Override
        public String generate(PipelineContext context) {
            yamlContext = context;
            return generatedYaml;
        }

        private ScenePlan.Scene scene(String id, int chapterIndex, String sourceChapterId, String title) {
            return new ScenePlan.Scene(
                    id,
                    "act_1",
                    chapterIndex,
                    sourceChapterId,
                    title,
                    "旧剧场",
                    "深夜",
                    List.of("林夏"),
                    "建立主角目标",
                    "是否进入旧剧场",
                    List.of(new ScenePlan.Beat(
                            "林夏推门进入。",
                            "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                            List.of("林夏")
                    )),
                    "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。"
            );
        }
    }

    private static final class CountingExecutor implements Executor {

        private final AtomicInteger executeCalls = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            executeCalls.incrementAndGet();
            command.run();
        }

        private int executeCalls() {
            return executeCalls.get();
        }
    }
}
