package com.aiplus.rag.controller;

import com.aiplus.rag.model.ChatRequest;
import com.aiplus.rag.model.ChatResponse;
import com.aiplus.rag.model.Citation;
import com.aiplus.rag.service.RagChatService;
import com.aiplus.rag.service.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        String question = request.getQuestion();

        log.info("收到流式问答请求: sessionId={}, question={}", sessionId, question);

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = sessionManager.createSession();
            log.info("自动创建新会话: {}", sessionId);
        } else if (!sessionManager.exists(sessionId)) {
            sessionId = sessionManager.createSession();
            log.info("原会话不存在，创建新会话: {}", sessionId);
        }

        sessionManager.updateSessionTitle(sessionId, question);

        final String finalSessionId = sessionId;
        final String finalQuestion = question;

        SseEmitter emitter = new SseEmitter(120000L);

        emitter.onCompletion(() -> log.info("SSE 连接完成: sessionId={}", finalSessionId));
        emitter.onTimeout(() -> log.warn("SSE 连接超时: sessionId={}", finalSessionId));
        emitter.onError((e) -> log.error("SSE 连接错误: sessionId={}, error={}", finalSessionId, e.getMessage()));

        new Thread(() -> {
            try {
                log.info("开始调用 RAG 服务...");
                long t0 = System.currentTimeMillis();
                ChatResponse response = ragChatService.chat(finalQuestion, finalSessionId);
                log.info("RAG 问答完成，耗时 {}ms", (System.currentTimeMillis() - t0));

                String answer = response.getAnswer();
                log.info("开始发送 SSE 事件，答案长度: {}", answer.length());
                int chunkSize = 20;

                int chunkCount = 0;
                for (int i = 0; i < answer.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, answer.length());
                    String data = objectMapper.writeValueAsString(
                            Map.of("type", "chunk", "content", answer.substring(i, end)));
                    emitter.send(SseEmitter.event().data(data));
                    chunkCount++;
                }
                log.info("已发送 {} 个 chunk 事件", chunkCount);

                for (Citation citation : response.getCitations()) {
                    String data = objectMapper.writeValueAsString(Map.of(
                            "type", "citation",
                            "citation", Map.of(
                                    "sourceFileName", citation.getSourceFileName(),
                                    "content", citation.getContent(),
                                    "relevanceScore", citation.getRelevanceScore()
                            )
                    ));
                    emitter.send(SseEmitter.event().data(data));
                }

                String doneData = objectMapper.writeValueAsString(Map.of(
                        "type", "done",
                        "sessionId", finalSessionId,
                        "answer", answer,
                        "citations", response.getCitations()
                ));
                emitter.send(SseEmitter.event().data(doneData));
                log.info("已发送 done 事件，完成 SSE 流");
                emitter.complete();

            } catch (Exception e) {
                log.error("流式问答处理异常", e);
                try {
                    String errorData = objectMapper.writeValueAsString(
                            Map.of("type", "error", "error", e.getMessage()));
                    emitter.send(SseEmitter.event()
                            .data(errorData, MediaType.APPLICATION_JSON));
                } catch (IOException sendError) {
                    log.error("发送错误事件也失败", sendError);
                }
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}