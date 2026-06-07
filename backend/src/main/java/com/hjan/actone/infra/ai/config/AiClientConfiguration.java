package com.hjan.actone.infra.ai.config;

import com.hjan.actone.ai.prompt.PromptTemplateService;
import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.infra.ai.ChatClient;
import com.hjan.actone.infra.ai.DefaultLLMService;
import com.hjan.actone.infra.ai.error.ModelClientExceptionMapper;
import com.hjan.actone.infra.ai.model.ModelHealthStore;
import com.hjan.actone.infra.ai.model.ModelRoutingExecutor;
import com.hjan.actone.infra.ai.model.ModelSelector;
import com.hjan.actone.infra.ai.prompt.ClasspathPromptTemplateService;
import com.hjan.actone.infra.ai.springai.SpringAiChatClient;
import com.hjan.actone.infra.ai.springai.SpringAiProviderIds;
import com.hjan.actone.infra.ai.support.LLMResponseCleaner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.util.List;

@Configuration
@EnableConfigurationProperties(AiModelProperties.class)
public class AiClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    public ChatClient chatClient(AiModelProperties properties) {
        return new SpringAiChatClient(
                SpringAiProviderIds.OPENAI_COMPATIBLE,
                properties.getSpringAi().getChat()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    public ModelHealthStore modelHealthStore(AiModelProperties properties) {
        return new ModelHealthStore(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    public ModelSelector modelSelector(AiModelProperties properties, ModelHealthStore healthStore) {
        return new ModelSelector(properties, healthStore);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    public ModelRoutingExecutor modelRoutingExecutor(ModelHealthStore healthStore) {
        return new ModelRoutingExecutor(healthStore);
    }

    @Bean
    public LLMResponseCleaner llmResponseCleaner() {
        return new LLMResponseCleaner();
    }

    @Bean
    public ModelClientExceptionMapper modelClientExceptionMapper() {
        return new ModelClientExceptionMapper();
    }

    @Bean
    public PromptTemplateService promptTemplateService(ResourceLoader resourceLoader) {
        return new ClasspathPromptTemplateService(resourceLoader);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    public LLMService llmService(ModelSelector selector,
                                 ModelRoutingExecutor routingExecutor,
                                 List<ChatClient> chatClients,
                                 LLMResponseCleaner responseCleaner,
                                 ModelClientExceptionMapper exceptionMapper) {
        return new DefaultLLMService(selector, routingExecutor, chatClients, responseCleaner, exceptionMapper);
    }
}
