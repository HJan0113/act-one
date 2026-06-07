package com.hjan.actone.script.service;

import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.script.ai.model.PipelineStage;
import com.hjan.actone.script.ai.pipeline.ScriptAiGenerationMode;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineService;
import com.hjan.actone.script.ai.support.FakeScriptAiStages;
import com.hjan.actone.script.ai.support.ScriptAiTestFixtures;
import com.hjan.actone.script.model.ScriptYamlGenerateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Iterator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptYamlGenerationServiceTest {

    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptYamlValidator yamlValidator = new ScriptYamlValidator();
    private final ScriptYamlDraftService ruleBasedDraftService = new ScriptYamlDraftService(chapterParser);

    @Test
    void generateFromTextUsesRuleBasedScaffoldWhenAiPipelineIsUnavailable() {
        ScriptYamlGenerationService service = serviceWithoutPipeline();

        var response = service.generateFromText(request());

        assertThat(response.generationMode()).isEqualTo(ScriptAiGenerationMode.RULE_BASED_SCAFFOLD.yamlValue());
        assertThat(response.fallback()).isFalse();
        assertThat(response.fallbackReason()).isNull();
        assertThat(response.validationStatus()).isEqualTo(ScriptYamlValidator.VALIDATION_STATUS_VALID);
        assertThat(response.validationError()).isNull();
        assertThat(response.yaml()).contains("generation_mode: \"rule_based_scaffold\"");
    }

    @Test
    void generateFromTextUsesAiPipelineWhenAvailable() {
        ScriptYamlGenerationService service = serviceWithPipeline(new FakeScriptAiStages());

        var response = service.generateFromText(request());

        assertThat(response.generationMode()).isEqualTo(ScriptAiGenerationMode.AI_PIPELINE.yamlValue());
        assertThat(response.fallback()).isFalse();
        assertThat(response.fallbackReason()).isNull();
        assertThat(response.validationStatus()).isEqualTo(ScriptYamlValidator.VALIDATION_STATUS_VALID);
        assertThat(response.validationError()).isNull();
        assertThat(response.yaml()).contains("generation_mode: \"ai_pipeline\"");
    }

    @Test
    void generateFromTextExposesExplicitFallbackWhenAiPipelineFails() {
        FakeScriptAiStages stages = new FakeScriptAiStages()
                .failingAt(
                        PipelineStage.SCRIPT_YAML_GENERATION,
                        new RemoteException("provider api-key=secret body=完整小说正文", BaseErrorCode.REMOTE_ERROR)
                );
        ScriptYamlGenerationService service = serviceWithPipeline(stages);

        var response = service.generateFromText(request());

        assertThat(response.generationMode()).isEqualTo(ScriptAiGenerationMode.RULE_BASED_FALLBACK.yamlValue());
        assertThat(response.fallback()).isTrue();
        assertThat(response.fallbackReason())
                .contains("SCRIPT_YAML_GENERATION")
                .doesNotContain("api-key", "secret", "完整小说正文");
        assertThat(response.validationStatus()).isEqualTo(ScriptYamlValidator.VALIDATION_STATUS_VALID);
        assertThat(response.yaml())
                .contains("generation_mode: \"rule_based_fallback\"")
                .doesNotContain("generation_mode: \"rule_based_scaffold\"");
    }

    private ScriptYamlGenerateRequest request() {
        return ScriptAiTestFixtures.sampleRequest();
    }

    private ScriptYamlGenerationService serviceWithoutPipeline() {
        return new ScriptYamlGenerationService(
                ruleBasedDraftService,
                yamlValidator,
                new SingleObjectProvider<>(null, ScriptAiPipelineService.class)
        );
    }

    private ScriptYamlGenerationService serviceWithPipeline(FakeScriptAiStages stages) {
        return new ScriptYamlGenerationService(
                ruleBasedDraftService,
                yamlValidator,
                new SingleObjectProvider<>(pipeline(stages), ScriptAiPipelineService.class)
        );
    }

    private ScriptAiPipelineService pipeline(FakeScriptAiStages stages) {
        return new ScriptAiPipelineService(
                chapterParser,
                yamlValidator,
                ruleBasedDraftService,
                stages,
                stages,
                stages
        );
    }

    private record SingleObjectProvider<T>(T object, Class<T> type) implements ObjectProvider<T> {

        @Override
        public T getObject(Object... args) {
            return getObject();
        }

        @Override
        public T getIfAvailable() {
            return object;
        }

        @Override
        public T getIfUnique() {
            return object;
        }

        @Override
        public T getObject() {
            if (object == null) {
                throw new NoSuchBeanDefinitionException(type);
            }
            return object;
        }

        @Override
        public Iterator<T> iterator() {
            return stream().iterator();
        }

        @Override
        public Stream<T> stream() {
            return object == null ? Stream.empty() : Stream.of(object);
        }

        @Override
        public Stream<T> orderedStream() {
            return stream();
        }
    }
}
