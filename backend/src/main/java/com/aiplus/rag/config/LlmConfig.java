package com.aiplus.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Value("${langchain4j.open-ai.chat-model.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key:sk-546ff6c09e2b498e8b68482bfc217f95}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name:deepseek-chat}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.open-ai.chat-model.max-tokens:2048}")
    private Integer maxTokens;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        String effectiveApiKey = apiKey;
        if (effectiveApiKey == null || effectiveApiKey.isEmpty() || "sk-your-api-key-here".equals(effectiveApiKey)) {
            effectiveApiKey = "sk-546ff6c09e2b498e8b68482bfc217f95";
        }
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(effectiveApiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }
}
