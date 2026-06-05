package com.hjan.actone.infra.ai.config;

import com.hjan.actone.ai.service.LLMService;
import com.hjan.actone.infra.ai.ChatClient;
import com.hjan.actone.infra.ai.DefaultLLMService;
import com.hjan.actone.infra.ai.springai.SpringAiChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiModelProperties.class)
public class AiClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    public ChatClient chatClient(AiModelProperties properties) {
        return new SpringAiChatClient(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.spring-ai.chat", name = "enabled", havingValue = "true")
    public LLMService llmService(ChatClient chatClient) {
        return new DefaultLLMService(chatClient);
    }
}
