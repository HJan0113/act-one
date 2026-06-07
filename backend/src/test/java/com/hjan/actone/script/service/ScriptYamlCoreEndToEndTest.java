package com.hjan.actone.script.service;

import com.hjan.actone.script.ai.pipeline.ScriptAiGenerationMode;
import com.hjan.actone.script.ai.pipeline.ScriptAiPipelineService;
import com.hjan.actone.script.ai.support.FakeScriptAiStages;
import com.hjan.actone.script.ai.support.ScriptAiTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptYamlCoreEndToEndTest {

    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptYamlValidator yamlValidator = new ScriptYamlValidator();
    private final ScriptYamlDraftService ruleBasedDraftService = new ScriptYamlDraftService(chapterParser);

    @Test
    void generateFromTextRunsCoreAiPipelineToValidTraceableEditableYaml() {
        ScriptYamlGenerationService service = service(new FakeScriptAiStages());

        var response = service.generateFromText(ScriptAiTestFixtures.sampleRequest());

        assertThat(response.generationMode()).isEqualTo(ScriptAiGenerationMode.AI_PIPELINE.yamlValue());
        assertThat(response.fallback()).isFalse();
        assertThat(response.fallbackReason()).isNull();
        assertThat(response.validationStatus()).isEqualTo(ScriptYamlValidator.VALIDATION_STATUS_VALID);
        assertThat(response.validationError()).isNull();
        assertThat(response.chapterCount()).isEqualTo(3);
        assertThat(yamlValidator.validate(response.yaml()).valid()).isTrue();

        Map<String, Object> document = parseYaml(response.yaml());
        Map<String, Object> source = asMap(document.get("source"));
        List<Map<String, Object>> characters = asMapList(document.get("characters"));
        List<Map<String, Object>> scenes = asMapList(document.get("scenes"));

        assertThat(source)
                .containsEntry("chapter_count", 3)
                .containsEntry("generation_mode", "ai_pipeline");
        assertThat(characters).isNotEmpty();
        assertThat(scenes).hasSizeGreaterThanOrEqualTo(2);
        assertThat(scenes.stream()
                .collect(Collectors.groupingBy(scene -> (Integer) scene.get("chapter_index"), Collectors.counting())))
                .containsEntry(1, 2L);
        assertThat(scenes).allSatisfy(scene -> {
            assertThat(scene.get("source_chapter_id")).asString().startsWith("chapter_");
            assertThat(scene.get("source_excerpt")).asString().isNotBlank();
            assertThat(scene.get("action")).asString().isNotBlank();
            assertThat(scene.get("ai_expansion")).isInstanceOf(Boolean.class);
            assertThat(scene.get("dialogues")).isInstanceOf(List.class);
        });
        assertThat(scenes).anySatisfy(scene ->
                assertThat(asList(scene.get("dialogues"))).isNotEmpty()
        );
        assertThat(scenes).anySatisfy(scene ->
                assertThat(scene.get("action")).asString().contains("寒光")
        );
        assertThat(dialogues(scenes)).anySatisfy(dialogue ->
                assertThat(dialogue).containsEntry("line", "终于来了。")
        );
    }

    private ScriptYamlGenerationService service(FakeScriptAiStages stages) {
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String yaml) {
        Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    private List<Map<String, Object>> dialogues(List<Map<String, Object>> scenes) {
        return scenes.stream()
                .flatMap(scene -> asList(scene.get("dialogues")).stream())
                .map(this::asMap)
                .toList();
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
