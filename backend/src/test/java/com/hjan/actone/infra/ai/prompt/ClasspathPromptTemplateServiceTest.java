package com.hjan.actone.infra.ai.prompt;

import com.hjan.actone.ai.prompt.PromptTemplateId;
import com.hjan.actone.ai.prompt.RenderedPrompt;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathPromptTemplateServiceTest {

    private final ClasspathPromptTemplateService service =
            new ClasspathPromptTemplateService(new DefaultResourceLoader());

    @Test
    void loadsAndRendersAllVersionedTemplates() {
        for (PromptTemplateId templateId : PromptTemplateId.values()) {
            Map<String, Object> variables = variablesFor(templateId);

            String template = service.load(templateId);
            RenderedPrompt rendered = service.render(templateId, variables);

            assertThat(template).isNotBlank();
            assertThat(rendered.templateId()).isEqualTo(templateId.id());
            assertThat(rendered.version()).isEqualTo(templateId.version());
            assertThat(rendered.content())
                    .isNotBlank()
                    .doesNotContain("{{");
            assertThat(templateId.resourcePath())
                    .startsWith("prompt/v1/")
                    .endsWith(".st");
        }
    }

    @Test
    void rendersSpecialCharactersWithoutRegexReplacementSideEffects() {
        Map<String, Object> variables = variablesFor(PromptTemplateId.CHAPTER_SUMMARY);
        variables.put("chapter_content", "林夏说：价格是 $1，路径为 C:\\novel，结构为 {\"valid\":true}。");

        RenderedPrompt rendered = service.render(PromptTemplateId.CHAPTER_SUMMARY, variables);

        assertThat(rendered.content())
                .contains("价格是 $1")
                .contains("C:\\novel")
                .contains("{\"valid\":true}");
    }

    @Test
    void rejectsMissingRequiredVariable() {
        Map<String, Object> variables = variablesFor(PromptTemplateId.CHAPTER_SUMMARY);
        variables.remove("chapter_content");

        assertThatThrownBy(() -> service.render(PromptTemplateId.CHAPTER_SUMMARY, variables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt 缺少必填变量：chapter-summary -> chapter_content");
    }

    @Test
    void rejectsNullVariableValue() {
        Map<String, Object> variables = variablesFor(PromptTemplateId.YAML_REPAIR);
        variables.put("invalid_yaml", null);

        assertThatThrownBy(() -> service.render(PromptTemplateId.YAML_REPAIR, variables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt 缺少必填变量：yaml-repair -> invalid_yaml");
    }

    @Test
    void rejectsTemplateWhoseDeclaredVariablesDriftFromCatalog() {
        byte[] content = "仅包含 {{chapter_id}}".getBytes(StandardCharsets.UTF_8);
        ResourceLoader resourceLoader = fixedResourceLoader(new ByteArrayResource(content));
        ClasspathPromptTemplateService invalidService =
                new ClasspathPromptTemplateService(resourceLoader);

        assertThatThrownBy(() -> invalidService.load(PromptTemplateId.CHAPTER_SUMMARY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Prompt 模板变量声明不一致")
                .hasMessageContaining("chapter-summary");
    }

    @Test
    void rejectsMissingTemplateResource() {
        Resource missingResource = new ByteArrayResource(new byte[0]) {
            @Override
            public boolean exists() {
                return false;
            }
        };
        ClasspathPromptTemplateService missingService =
                new ClasspathPromptTemplateService(fixedResourceLoader(missingResource));

        assertThatThrownBy(() -> missingService.load(PromptTemplateId.YAML_REPAIR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Prompt 模板不存在：prompt/v1/yaml-repair.st");
    }

    private Map<String, Object> variablesFor(PromptTemplateId templateId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        for (String variable : templateId.variables()) {
            variables.put(variable, sampleValue(variable));
        }
        return variables;
    }

    private Object sampleValue(String variable) {
        return switch (variable) {
            case "chapter_index", "chapter_count" -> 3;
            case "chapter_id" -> "chapter_3";
            case "chapter_title" -> "第三章 暗门";
            case "chapter_content" -> "林夏推开暗门，发现失踪多年的道具箱。";
            case "chapter_summaries" -> "[{\"chapterId\":\"chapter_3\"}]";
            case "global_story_state" -> "{\"characters\":[]}";
            case "scene_plans" -> "{\"scenes\":[]}";
            case "project_title" -> "暗门";
            case "author" -> "测试作者";
            case "logline" -> "林夏追查旧剧场的秘密。";
            case "schema_summary" -> "schema_version、kind、characters、acts、scenes 为必填字段。";
            case "invalid_yaml" -> "kind screenplay_draft";
            case "validation_errors" -> "YAML 语法错误：缺少冒号";
            default -> "测试值";
        };
    }

    private ResourceLoader fixedResourceLoader(Resource resource) {
        return new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return resource;
            }

            @Override
            public ClassLoader getClassLoader() {
                return getClass().getClassLoader();
            }
        };
    }
}
