package com.aiplus.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    private static final String OLLAMA_MODEL = "nomic-embed-text";

    @Value("${rag.embedding.type:ollama}")
    private String embeddingType;

    @Value("${rag.embedding.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${rag.embedding.remote.base-url:}")
    private String remoteBaseUrl;

    @Value("${rag.embedding.remote.api-key:}")
    private String remoteApiKey;

    @Value("${rag.embedding.remote.model-name:text-embedding-ada-002}")
    private String remoteModelName;

    @Value("${rag.embedding.timeout-seconds:120}")
    private int timeoutSeconds;

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 Embedding 模型, 类型: {}, 超时: {}s", embeddingType, timeoutSeconds);

        if ("ollama".equalsIgnoreCase(embeddingType)) {
            log.info("使用 Ollama Embedding 模型: {} @ {}", OLLAMA_MODEL, ollamaBaseUrl);
            return OllamaEmbeddingModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(OLLAMA_MODEL)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        }

        if ("remote".equalsIgnoreCase(embeddingType)) {
            log.info("使用远程 OpenAI 兼容 Embedding: {} @ {}", remoteModelName, remoteBaseUrl);
            return OpenAiEmbeddingModel.builder()
                    .baseUrl(remoteBaseUrl)
                    .apiKey(remoteApiKey)
                    .modelName(remoteModelName)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        }

        throw new IllegalArgumentException(
                "不支持的 embedding 类型: " + embeddingType + "，可选值: ollama / remote");
    }
}
