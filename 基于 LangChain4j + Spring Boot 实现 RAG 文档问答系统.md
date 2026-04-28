# 基于 LangChain4j + Spring Boot 实现 RAG 文档问答系统

> 本文详细介绍如何基于 Spring Boot 3.2 和 LangChain4j 0.35 构建一个完整的 RAG（检索增强生成）文档问答系统。

## 📖 目录

1. [RAG 原理介绍](#1-rag-原理介绍)
2. [LangChain4j 核心组件](#2-langchain4j-核心组件)
3. [Spring Boot 集成配置](#3-spring-boot-集成配置)
4. [SSE 流式输出实现](#4-sse-流式输出实现)
5. [完整代码示例](#5-完整代码示例)

***

## 1. RAG 原理介绍

### 什么是 RAG？

**RAG（Retrieval-Augmented Generation）** 即检索增强生成，是一种将信息检索与语言模型生成相结合的技术架构。

### RAG 工作流程

```
┌──────────────────────────────────────────────────────────────┐
│                        RAG 工作流程                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                 │
│  │ 文档上传 │───▶│ 文档解析 │───▶│ 文本分割 │                 │
│  └──────────┘    └──────────┘    └────┬─────┘                 │
│                                       │                      │
│                                       ▼                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                 │
│  │  存储    │◀───│ 向量化   │◀───│ Embedding│                 │
│  │ 向量数据库│    └──────────┘    └──────────┘                 │
│  └────┬─────┘                                                │
│       │                                                      │
│       │  用户提问                                            │
│       ▼                                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                 │
│  │  语义    │───▶│  提示词   │───▶│  LLM     │───▶ 最终回答   │
│  │  检索    │    │  构建    │    │  生成    │                 │
│  └──────────┘    └──────────┘    └──────────┘                 │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 为什么需要 RAG？

1. **解决幻觉问题**：LLM 可能产生不准确的信息，RAG 通过检索真实文档来约束生成
2. **知识时效性**：可以随时更新文档，无需重新训练模型
3. **可溯源性**：回答基于检索到的文档，用户可以验证
4. **成本效益**：比微调模型更经济

***

## 2. LangChain4j 核心组件

LangChain4j 是 Java 版的 LangChain，提供了丰富的 AI 应用开发工具。

### 2.1 ChatLanguageModel

用于与大语言模型交互：

```java
// 配置 DeepSeek LLM
ChatLanguageModel chatModel = OpenAiChatModel.builder()
    .baseUrl("https://api.deepseek.com")
    .apiKey("your-api-key")
    .modelName("deepseek-chat")
    .temperature(0.7)
    .maxTokens(2000)
    .build();

// 简单对话
String response = chatModel.generate("你好，请介绍一下你自己");
```

### 2.2 EmbeddingModel

用于将文本转换为向量：

```java
// 配置 Ollama Embedding
EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("nomic-embed-text")
    .timeout(Duration.ofSeconds(60))
    .build();

// 生成文本向量
Embedding embedding = embeddingModel.embed("要向量化的文本").content();
```

### 2.3 EmbeddingStore

向量存储接口，支持多种后端：

```java
// 内存存储（适合开发和小规模应用）
EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

// 添加向量
embeddingStore.add(embedding, textSegment);

// 相似度搜索
EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
    EmbeddingSearchQuery.builder()
        .queryEmbedding(embedding)
        .maxResults(5)
        .build()
);
```

### 2.4 ContentRetriever

内容检索器，用于根据查询检索相关文档：

```java
// 创建检索器
ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(embeddingStore)
    .embeddingModel(embeddingModel)
    .maxResults(5)
    .minScore(0.5)
    .build();

// 检索相关文档
var contents = retriever.retrieve(Query.from("用户问题"));
```

***

## 3. Spring Boot 集成配置

### 3.1 Maven 依赖

```xml
<!-- Spring Boot 3.2 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.3</version>
</parent>

<!-- LangChain4j -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- DeepSeek 支持 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.35.0</version>
</dependency>

<!-- Ollama 支持 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>0.35.0</version>
</dependency>
```

### 3.2 配置文件

```yaml
# application.yml
langchain4j:
  deepseek:
    api-key: ${DEEPSEEK_API_KEY:your-api-key}
    base-url: https://api.deepseek.com
    model: deepseek-chat
    temperature: 0.7
    max-tokens: 2000
  
  ollama:
    base-url: http://localhost:11434
    model: nomic-embed-text
    timeout: 60s

ai:
  rag:
    top-k: 5
    max-chunk-size: 500
    chunk-overlap: 50
    min-similarity-score: 0.5
```

### 3.3 配置类

```java
@Configuration
public class LlmConfig {
    
    @Value("${langchain4j.deepseek.api-key}")
    private String apiKey;
    
    @Value("${langchain4j.deepseek.base-url}")
    private String baseUrl;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .modelName("deepseek-chat")
            .temperature(0.7)
            .maxTokens(2000)
            .timeout(Duration.ofSeconds(60))
            .build();
    }
}
```

***

## 4. SSE 流式输出实现

### 什么是 SSE？

**Server-Sent Events (SSE)** 是一种服务器向浏览器推送数据的技术，与 WebSocket 不同，SSE 是单向的，更适合聊天场景。

### SSE vs WebSocket

| 特性   | SSE     | WebSocket     |
| ---- | ------- | ------------- |
| 方向   | 服务器→客户端 | 双向            |
| 协议   | HTTP    | 独立的 ws\:// 协议 |
| 重连   | 自动重连    | 需要手动处理        |
| 防火墙  | 容易被允许   | 可能被阻止         |
| 适用场景 | 聊天推送、通知 | 实时游戏、协作       |

### 后端 SSE 实现

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public void streamChat(@RequestBody ChatRequest request, 
                        HttpServletResponse response) throws IOException {
    
    // 设置 SSE 响应头
    response.setContentType("text/event-stream");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Connection", "keep-alive");

    PrintWriter writer = response.getWriter();
    
    // 逐步发送内容
    chatModel.generate(prompt, new StreamingResponseHandler() {
        @Override
        public void onPartialResponse(String partialText) {
            try {
                // 发送 SSE 格式数据
                String data = String.format("data: %s\n\n", 
                    escapeJson(partialText));
                writer.write(data);
                writer.flush();
            } catch (IOException e) {
                log.error("SSE 发送失败", e);
            }
        }

        @Override
        public void onComplete(Response<String> response) {
            try {
                writer.write("data: [DONE]\n\n");
                writer.flush();
            } catch (IOException e) {
                log.error("SSE 完成失败", e);
            }
        }
    });
}
```

***

## 5. 完整代码示例

### 5.1 RAG 聊天服务核心类

```java
@Service
@Slf4j
public class RagChatService {

    private final ChatLanguageModel chatModel;
    private final ContentRetriever contentRetriever;
    private final RagPromptTemplate promptTemplate;
    private final SessionManager sessionManager;

    public RagChatService(
            ChatLanguageModel chatModel,
            ContentRetriever contentRetriever,
            RagPromptTemplate promptTemplate,
            SessionManager sessionManager) {
        this.chatModel = chatModel;
        this.contentRetriever = contentRetriever;
        this.promptTemplate = promptTemplate;
        this.sessionManager = sessionManager;
    }

    /**
     * RAG 聊天流程
     */
    public ChatResponse chat(ChatRequest request) {
        String userMessage = request.getMessage();
        String sessionId = request.getSessionId();

        // 1️⃣ 语义检索：找到相关文档片段
        List<Citation> citations = retrieveCitations(userMessage);

        // 2️⃣ 构建 RAG 提示词
        String prompt = promptTemplate.build(userMessage, citations);

        // 3️⃣ 调用 LLM 生成回答
        String answer = chatModel.generate(prompt).content();

        // 4️⃣ 保存对话历史
        sessionManager.addMessage(sessionId, createUserMessage(userMessage));
        sessionManager.addMessage(sessionId, createAssistantMessage(answer, citations));

        return ChatResponse.builder()
                .content(answer)
                .citations(citations)
                .sessionId(sessionId)
                .done(true)
                .build();
    }

    /**
     * 检索相关文档并构建引用
     */
    private List<Citation> retrieveCitations(String query) {
        List<Citation> citations = new ArrayList<>();
        
        // 使用 ContentRetriever 检索
        var contents = contentRetriever.retrieve(Query.from(query));
        
        for (var result : contents) {
            Citation citation = Citation.builder()
                    .documentId(result.content().metadata().getString("documentId"))
                    .documentName(result.content().metadata().getString("documentName"))
                    .text(truncateText(result.content().text(), 200))
                    .score(result.score())
                    .build();
            citations.add(citation);
        }
        
        return citations;
    }
}
```

### 5.2 RAG 提示词模板

```java
@Component
public class RagPromptTemplate {

    public String build(String question, List<Citation> citations) {
        StringBuilder prompt = new StringBuilder();

        // 系统指令
        prompt.append("你是一个专业的文档问答助手。");
        prompt.append("请根据提供的参考文档内容，准确回答用户的问题。\n\n");

        // 添加检索到的文档内容
        if (citations != null && !citations.isEmpty()) {
            prompt.append("【参考文档】\n");
            
            for (int i = 0; i < citations.size(); i++) {
                Citation c = citations.get(i);
                prompt.append(String.format("[%d] %s\n%s\n\n", 
                    i + 1, c.getDocumentName(), c.getText()));
            }

            prompt.append("【回答要求】\n");
            prompt.append("1. 基于上述参考文档回答问题\n");
            prompt.append("2. 如有相关内容，请注明来源\n");
            prompt.append("3. 如文档中没有相关信息，请如实说明\n\n");
        }

        // 用户问题
        prompt.append("【问题】\n").append(question);

        return prompt.toString();
    }
}
```

### 5.3 向量化服务

```java
@Service
@RequiredArgsConstructor
public class VectorizationService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 将文档内容向量化并存储
     */
    public void vectorize(DocumentParseResult result) {
        String documentId = result.getDocumentId();
        
        for (TextChunk chunk : result.getChunks()) {
            // 创建带元数据的文本片段
            Map<String, Object> metadata = Map.of(
                "documentId", documentId,
                "documentName", result.getFilename(),
                "chunkId", chunk.getId()
            );
            
            TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
            
            // 生成向量
            Embedding embedding = embeddingModel.embed(segment).content();
            
            // 存储
            embeddingStore.add(embedding, segment);
        }
        
        log.info("文档向量化完成: {}, 块数: {}", documentId, result.getChunks().size());
    }
}
```

***

## 📚 总结

本文介绍了基于 Spring Boot + LangChain4j 构建 RAG 文档问答系统的完整方案：

1. **RAG 架构**：通过检索增强生成，解决 LLM 幻觉问题，提供可溯源的回答
2. **LangChain4j**：提供统一的 AI 集成接口，支持多种 LLM 和 Embedding 模型
3. **SSE 流式输出**：实现打字机效果，提升用户体验
4. **完整代码示例**：包含 RAG 服务、提示词模板、向量化等核心组件

***

## 🔗 相关资源

- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [DeepSeek API 文档](https://platform.deepseek.com/)
- [Ollama 官网](https://ollama.ai/)

***

> 💡 提示：生产环境请使用专业的向量数据库（如 Elasticsearch、Pinecone）替代内存存储，并添加适当的缓存和安全措施。

