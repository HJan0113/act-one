package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.script.ai.model.GlobalStoryState;
import com.hjan.actone.script.ai.model.PipelineContext;
import com.hjan.actone.script.ai.model.ScenePlan;
import com.hjan.actone.script.ai.model.SourceEvidence;
import com.hjan.actone.script.ai.model.SourceTrace;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptYamlFactConstraintCheckerTest {

    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptYamlFactConstraintChecker checker = new ScriptYamlFactConstraintChecker();

    @Test
    void acceptsSourceBackedYamlWithoutAiExpansion() {
        ScriptYamlValidationResult result = checker.validate(
                context(),
                yaml(
                        "林夏推开旧剧场的门。",
                        "    ai_expansion: false\n",
                        " []",
                        "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                        knownCharactersBlock()
                ),
                chapters()
        );

        assertThat(result.valid()).isTrue();
    }

    @Test
    void acceptsCreativeRewriteWhenMarkedAsAiExpansion() {
        ScriptYamlValidationResult result = checker.validate(
                context(),
                yaml(
                        "林夏停在门口，指尖压皮信封，视线越过空荡舞台。",
                        "    ai_expansion: true\n",
                        """

                              - speaker: "林夏"
                                speaker_character_id: "character_001"
                                line: "我进去。你在这里等我。"
                                emotion: "克制"
                        """,
                        "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                        knownCharactersBlock()
                ),
                chapters()
        );

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsCreativeRewriteWithoutAiExpansionMarker() {
        ScriptYamlValidationResult result = checker.validate(
                context(),
                yaml(
                        "林夏停在门口，指尖压皮信封，视线越过空荡舞台。",
                        "",
                        " []",
                        "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                        knownCharactersBlock()
                ),
                chapters()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage())
                .startsWith(ScriptYamlFactConstraintChecker.ERROR_PREFIX)
                .contains("ai_expansion");
    }

    @Test
    void rejectsUnknownCharactersAndSpeakers() {
        ScriptYamlValidationResult result = checker.validate(
                context(),
                yaml(
                        "顾北冲进旧剧场，夺走那封信。",
                        "    ai_expansion: true\n",
                        """

                              - speaker: "顾北"
                                speaker_character_id: "character_999"
                                line: "这封信归我。"
                                emotion: "急促"
                        """,
                        "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                        """
                          - id: "character_999"
                            name: "顾北"
                            role: "无来源人物"
                            """
                ),
                chapters()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage())
                .contains("无来源人物")
                .contains("无来源说话人");
    }

    @Test
    void rejectsSourceExcerptThatCannotBeTracedToNovel() {
        ScriptYamlValidationResult result = checker.validate(
                context(),
                yaml(
                        "林夏推开旧剧场的门。",
                        "    ai_expansion: true\n",
                        " []",
                        "林夏在海边遇见陌生人。",
                        knownCharactersBlock()
                ),
                chapters()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("source_excerpt 无法回查源文");
    }

    @Test
    void rejectsKnownCharacterThatSuddenlyAppearsOutsidePlannedScene() {
        ScriptYamlValidationResult result = checker.validate(
                context(),
                yaml(
                        "旧友从后台暗门走出，挡住林夏。",
                        "    ai_expansion: true\n",
                        """

                              - speaker: "旧友"
                                speaker_character_id: "character_002"
                                line: "别再往前。"
                                emotion: "警惕"
                        """,
                        "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                        knownCharactersBlock()
                ),
                chapters()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage())
                .contains("未列入出场人物")
                .contains("说话人未在场景出场列表");
    }

    private PipelineContext context() {
        return PipelineContext.create(null, "测试剧本", "作者A", "主角在旧剧场面对选择。", 3)
                .withGlobalStoryState(globalStoryState())
                .withScenePlan(scenePlan())
                .withSourceTrace(sourceTrace());
    }

    private GlobalStoryState globalStoryState() {
        return new GlobalStoryState(
                List.of(
                        new GlobalStoryState.CharacterProfile(
                                "character_001",
                                "林夏",
                                List.of("小夏"),
                                "主角",
                                "旧剧场匿名信事件的调查者。",
                                "chapter_1",
                                List.of("chapter_1")
                        ),
                        new GlobalStoryState.CharacterProfile(
                                "character_002",
                                "旧友",
                                List.of(),
                                "知情者",
                                "多年未见的朋友。",
                                "chapter_2",
                                List.of("chapter_2")
                        )
                ),
                List.of(new GlobalStoryState.LocationProfile(
                        "location_001",
                        "旧剧场",
                        "匿名信出现的核心地点。",
                        List.of("chapter_1")
                )),
                List.of(),
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
                "旧剧场",
                "旧剧场",
                "深夜",
                List.of("character_001"),
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

    private SourceTrace sourceTrace() {
        return new SourceTrace(List.of(new SourceTrace.SceneTrace(
                "scene_001",
                1,
                "chapter_1",
                "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。",
                List.of(new SourceEvidence(
                        "chapter_1",
                        1,
                        "林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。"
                ))
        )));
    }

    private List<NovelChapter> chapters() {
        return chapterParser.parse("""
                第 1 章 开端
                林夏推开旧剧场的门，发现舞台中央留着一封没有署名的信。

                第 2 章 发展
                她按照信里的线索找到后台暗门，却被多年未见的朋友拦住。

                第 3 章 选择
                演出开始前，林夏必须决定公开真相，还是保护朋友的秘密。
                """);
    }

    private String knownCharactersBlock() {
        return """
          - id: "character_001"
            name: "林夏"
            role: "主角"
            """;
    }

    private String yaml(String action,
                        String aiExpansionLine,
                        String dialogues,
                        String sourceExcerpt,
                        String charactersBlock) {
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
                %sacts:
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
                    time: "深夜"
                    scene_purpose: "建立主角目标"
                    action: "%s"
                %s    dialogues:%s
                    source_excerpt: "%s"
                """.formatted(charactersBlock, action, aiExpansionLine, dialogues, sourceExcerpt);
    }
}
