package com.hjan.actone.infra.ai.config;

import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.infra.ai.ChatClient;
import com.hjan.actone.infra.ai.error.ModelClientExceptionMapper;
import com.hjan.actone.infra.ai.model.ModelHealthStore;
import com.hjan.actone.infra.ai.model.ModelRoutingExecutor;
import com.hjan.actone.infra.ai.model.ModelSelector;
import com.hjan.actone.infra.ai.support.LLMResponseCleaner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiClientConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiClientConfiguration.class)
            .withPropertyValues(
                    "ai.providers.siliconflow.url=https://mock.example",
                    "ai.providers.siliconflow.api-key=test-api-key",
                    "ai.providers.siliconflow.endpoints.chat=/v1/chat/completions",
                    "ai.providers.siliconflow.endpoints.embedding=/v1/embeddings",
                    "ai.spring-ai.chat.enabled=true",
                    "ai.spring-ai.chat.temperature=0.2",
                    "ai.spring-ai.chat.top-p=0.8",
                    "ai.spring-ai.chat.max-tokens=1024",
                    "ai.spring-ai.chat.timeout=30s",
                    "ai.chat.default-model=spring-ai-test",
                    "ai.chat.candidates[0].id=spring-ai-test",
                    "ai.chat.candidates[0].provider=siliconflow",
                    "ai.chat.candidates[0].model=test-model",
                    "ai.chat.candidates[0].enabled=true",
                    "ai.chat.candidates[0].priority=1",
                    "ai.selection.failure-threshold=3",
                    "ai.selection.open-duration-ms=1500"
            );

    @Test
    void registersSpringAiClientAndLlmService() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiModelProperties.class);
            assertThat(context).hasSingleBean(ChatClient.class);
            assertThat(context).hasSingleBean(LLMService.class);
            assertThat(context).hasSingleBean(ModelHealthStore.class);
            assertThat(context).hasSingleBean(ModelSelector.class);
            assertThat(context).hasSingleBean(ModelRoutingExecutor.class);
            assertThat(context).hasSingleBean(PromptTemplateService.class);
            assertThat(context).hasSingleBean(LLMResponseCleaner.class);
            assertThat(context).hasSingleBean(ModelClientExceptionMapper.class);

            AiModelProperties properties = context.getBean(AiModelProperties.class);
            assertThat(properties.getProviders().get("siliconflow").getUrl())
                    .isEqualTo("https://mock.example");
            assertThat(properties.getProviders().get("siliconflow").getEndpoints().get("chat"))
                    .isEqualTo("/v1/chat/completions");
            assertThat(properties.getChat().getDefaultModel()).isEqualTo("spring-ai-test");
            assertThat(properties.getChat().getCandidates().get(0).getModel()).isEqualTo("test-model");
            assertThat(properties.getSpringAi().getChat().getTimeout()).hasSeconds(30);
            assertThat(properties.getSelection().getFailureThreshold()).isEqualTo(3);
            assertThat(properties.getSelection().getOpenDurationMs()).isEqualTo(1500);
            assertThat(context.getBean(ChatClient.class).provider())
                    .isEqualTo("spring-ai-openai-compatible");
        });
    }

    @Test
    void registersPromptServiceWhenRealChatClientIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiClientConfiguration.class)
                .withPropertyValues("ai.spring-ai.chat.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PromptTemplateService.class);
                    assertThat(context).doesNotHaveBean(ChatClient.class);
                    assertThat(context).doesNotHaveBean(LLMService.class);
                    assertThat(context).doesNotHaveBean(ModelSelector.class);
                    assertThat(context).doesNotHaveBean(ModelRoutingExecutor.class);
                });
    }

    @Test
    void applicationUsesRagentStyleBaiLianConfiguration() throws Exception {
        Path applicationYaml = applicationYamlPath();
        String yaml = Files.readString(applicationYaml);

        assertThat(yaml)
                .doesNotContain("api.openai.com", "replace-with-api-key", "replace-with-model-name")
                .contains(
                        "ai:",
                        "providers:",
                        "bailian:",
                        "url: https://dashscope.aliyuncs.com",
                        "api-key: ${BAILIAN_API_KEY:}",
                        "chat: /compatible-mode/v1/chat/completions",
                        "default-model: qwen3.7-plus",
                        "id: qwen3-max",
                        "id: deepseek-v4-flash",
                        "provider: bailian",
                        "model: qwen3-max",
                        "model: deepseek-v4-flash",
                        "selection:",
                        "failure-threshold: 2",
                        "open-duration-ms: 30000"
                );
        assertThat(yaml).doesNotContain("rerank:", "embedding:");

        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "application",
                new FileSystemResource(applicationYaml)
        );
        String defaultModel = property(propertySources, "ai.chat.default-model");
        String candidateProvider = providerForCandidate(propertySources, defaultModel);

        assertThat(candidateProvider).isNotBlank();
        assertThat(propertySources)
                .anySatisfy(source -> assertThat(source.containsProperty(
                        "ai.providers." + candidateProvider + ".url"
                )).isTrue());
    }

    private Path applicationYamlPath() {
        Path fromBackend = Path.of("src/main/resources/application.yml");
        if (Files.exists(fromBackend)) {
            return fromBackend;
        }
        return Path.of("backend/src/main/resources/application.yml");
    }

    private String property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .map(Object::toString)
                .findFirst()
                .orElse(null);
    }

    private String providerForCandidate(List<PropertySource<?>> sources, String candidateId) {
        for (int index = 0; index < 10; index++) {
            String id = property(sources, "ai.chat.candidates[" + index + "].id");
            if (id == null) {
                return null;
            }
            if (candidateId.equals(id)) {
                return property(sources, "ai.chat.candidates[" + index + "].provider");
            }
        }
        return null;
    }
}
