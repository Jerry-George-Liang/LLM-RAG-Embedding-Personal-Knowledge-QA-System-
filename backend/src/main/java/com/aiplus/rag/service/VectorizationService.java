package com.aiplus.rag.service;

import com.aiplus.rag.model.DocumentMetadata;
import com.aiplus.rag.model.DocumentParseResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorizationService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitService documentSplitService;
    private final KeywordSearchService keywordSearchService;
    private final ConcurrentHashMap<String, DocumentMetadata> documentRegistry;
    private final ConcurrentHashMap<String, List<String>> documentEmbeddingIds = new ConcurrentHashMap<>();

    @Value("${rag.vectorization.batch-size:20}")
    private int batchSize;

    public DocumentMetadata vectorize(DocumentParseResult parseResult) {
        String fileName = parseResult.getFileName();
        log.info("开始向量化文件: {}，原始段落数: {}", fileName, parseResult.getSegmentCount());

        long startTime = System.currentTimeMillis();

        List<TextSegment> segments = documentSplitService.split(
                parseResult.getDocuments(), fileName);

        if (segments.isEmpty()) {
            log.warn("文件 {} 切分后无有效段落，跳过向量化", fileName);
            DocumentMetadata emptyMeta = DocumentMetadata.builder()
                    .documentId(parseResult.getDocumentId())
                    .fileName(fileName)
                    .uploadTime(LocalDateTime.now())
                    .segmentCount(0)
                    .build();
            documentRegistry.put(parseResult.getDocumentId(), emptyMeta);
            return emptyMeta;
        }

        int totalSegments = segments.size();
        List<String> allEmbeddingIds = new ArrayList<>();
        int processedBatches = 0;

        for (int i = 0; i < totalSegments; i += batchSize) {
            int end = Math.min(i + batchSize, totalSegments);
            List<TextSegment> batchSegments = segments.subList(i, end);
            int batchNum = (i / batchSize) + 1;
            int totalBatches = (totalSegments + batchSize - 1) / batchSize;

            log.info("处理批次 {}/{}: 段落 {}-{}, 共{}个段落",
                    batchNum, totalBatches, i + 1, end, batchSegments.size());

            try {
                long batchStart = System.currentTimeMillis();
                List<Embedding> embeddings = embeddingModel.embedAll(batchSegments).content();
                log.info("批次 {} Embedding 完成, 耗时: {}ms",
                        batchNum, System.currentTimeMillis() - batchStart);

                batchStart = System.currentTimeMillis();
                List<String> batchIds = embeddingStore.addAll(embeddings, batchSegments);
                if (batchIds != null) {
                    allEmbeddingIds.addAll(batchIds);
                }
                log.info("批次 {} 向量存储写入完成, 耗时: {}ms",
                        batchNum, System.currentTimeMillis() - batchStart);

                processedBatches++;
            } catch (Exception e) {
                log.error("批次 {} 处理失败: {}", batchNum, e.getMessage(), e);
                throw new RuntimeException("向量化失败（批次 " + batchNum + " 错误）: " + e.getMessage(), e);
            }
        }

        documentEmbeddingIds.put(parseResult.getDocumentId(), allEmbeddingIds);

        for (int i = 0; i < segments.size(); i++) {
            String segmentId = parseResult.getDocumentId() + "_seg_" + i;
            keywordSearchService.indexSegment(segmentId, segments.get(i));
        }

        DocumentMetadata metadata = DocumentMetadata.builder()
                .documentId(parseResult.getDocumentId())
                .fileName(fileName)
                .uploadTime(LocalDateTime.now())
                .segmentCount(totalSegments)
                .build();

        documentRegistry.put(parseResult.getDocumentId(), metadata);

        long totalMs = System.currentTimeMillis() - startTime;
        log.info("向量化完成: {}，最终段落数: {}，总批次数: {}，向量维度: {}，总耗时: {}ms",
                fileName, totalSegments, processedBatches, embeddingModel.dimension(), totalMs);

        return metadata;
    }

    public List<DocumentMetadata> listDocuments() {
        return List.copyOf(documentRegistry.values());
    }

    public void clearAll() {
        List<String> allIds = documentEmbeddingIds.values().stream()
                .flatMap(List::stream)
                .toList();
        if (!allIds.isEmpty()) {
            try {
                embeddingStore.removeAll(allIds);
            } catch (Exception e) {
                log.error("清除向量数据失败: {}", e.getMessage(), e);
            }
        }
        documentRegistry.clear();
        documentEmbeddingIds.clear();
        keywordSearchService.clearAll();
        log.info("已清除所有向量数据和文档元数据");
    }

    public boolean deleteDocument(String documentId) {
        DocumentMetadata metadata = documentRegistry.get(documentId);
        if (metadata == null) {
            log.warn("文档不存在: {}", documentId);
            return false;
        }

        List<String> idsToRemove = documentEmbeddingIds.get(documentId);
        if (idsToRemove != null && !idsToRemove.isEmpty()) {
            try {
                embeddingStore.removeAll(idsToRemove);
            } catch (Exception e) {
                log.error("删除文档向量数据失败: {}", e.getMessage(), e);
            }
            documentEmbeddingIds.remove(documentId);
        }

        documentRegistry.remove(documentId);
        log.info("已删除文档: {}，移除 {} 个段落", metadata.getFileName(),
                idsToRemove != null ? idsToRemove.size() : 0);
        return true;
    }

    public int getTotalSegments() {
        return documentRegistry.values().stream()
                .mapToInt(DocumentMetadata::getSegmentCount)
                .sum();
    }
}
