package com.hjan.actone.script.ai.pipeline;

import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.support.FakeScriptAiStages;
import com.hjan.actone.script.ai.support.ScriptAiTestFixtures;
import com.hjan.actone.script.service.NovelChapterParser;
import com.hjan.actone.script.service.ScriptYamlDraftService;
import com.hjan.actone.script.service.ScriptYamlValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptAiPipelineFakeLlmTest {

    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptYamlValidator yamlValidator = new ScriptYamlValidator();
    private final ScriptYamlDraftService ruleBasedDraftService = new ScriptYamlDraftService(chapterParser);

    @Test
    void sampleNovelFixtureTracksNoticeSampleWhenAvailable() throws IOException {
        String fixtureText = ScriptAiTestFixtures.sampleNovelText();
        Path noticeSample = Path.of("../notice/测试小说文本.txt");

        assertThat(fixtureText)
                .contains("第一章：夜雨孤庙")
                .contains("第三章：拔剑易主");
        if (Files.exists(noticeSample)) {
            String noticeText = Files.readString(noticeSample).strip();
            if (noticeText.contains("第一章：夜雨孤庙")) {
                assertThat(fixtureText.strip()).isEqualTo(noticeText);
            }
        }
    }

    @Test
    void fakeLlmNormalResponseRunsWithoutApiKeyOrNetwork() {
        FakeScriptAiStages stages = new FakeScriptAiStages();

        ScriptAiPipelineResult result = service(stages).generate(ScriptAiTestFixtures.sampleRequest());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isFalse();
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.AI_PIPELINE);
        assertThat(result.yaml())
                .contains("generation_mode: \"ai_pipeline\"")
                .contains("破庙伏杀");
        assertThat(stages.summaryCalls()).isEqualTo(3);
        assertThat(stages.scenePlanningContext()).isNotNull();
        assertThat(stages.yamlGenerationContext()).isNotNull();
    }

    @Test
    void fakeLlmBlankYamlResponseFallsBackExplicitly() {
        FakeScriptAiStages stages = new FakeScriptAiStages()
                .generatedYaml("   ");

        ScriptAiPipelineResult result = service(stages).generate(ScriptAiTestFixtures.sampleRequest());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isTrue();
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.RULE_BASED_FALLBACK);
        assertThat(result.fallbackReason()).contains("SCRIPT_YAML_GENERATION");
        assertThat(result.yaml())
                .contains("generation_mode: \"rule_based_fallback\"")
                .doesNotContain("generation_mode: \"rule_based_scaffold\"");
    }

    @Test
    void fakeLlmTimeoutFallsBackWithoutLeakingProviderMessage() {
        FakeScriptAiStages stages = new FakeScriptAiStages()
                .failingAt(
                        PipelineStage.SCRIPT_YAML_GENERATION,
                        new RemoteException(
                                "Read timed out, api-key=secret, body=完整小说正文",
                                BaseErrorCode.SERVICE_TIMEOUT_ERROR
                        )
                );

        ScriptAiPipelineResult result = service(stages).generate(ScriptAiTestFixtures.sampleRequest());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason())
                .contains("SCRIPT_YAML_GENERATION")
                .doesNotContain("api-key", "secret", "完整小说正文");
        assertThat(result.yaml()).contains("generation_mode: \"rule_based_fallback\"");
    }

    @Test
    void fakeLlmIllegalYamlResponseFallsBackWithoutRepair() {
        FakeScriptAiStages stages = new FakeScriptAiStages()
                .generatedYaml(ScriptAiTestFixtures.invalidYaml());

        ScriptAiPipelineResult result = service(stages).generate(ScriptAiTestFixtures.sampleRequest());

        assertThat(result.valid()).isTrue();
        assertThat(result.fallback()).isTrue();
        assertThat(result.generationMode()).isEqualTo(ScriptAiGenerationMode.RULE_BASED_FALLBACK);
        assertThat(result.fallbackReason()).contains("YAML Schema 校验失败");
        assertThat(result.yaml()).contains("generation_mode: \"rule_based_fallback\"");
    }

    private ScriptAiPipelineService service(FakeScriptAiStages stages) {
        return new ScriptAiPipelineService(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                stages,
                stages,
                stages
        );
    }
}
