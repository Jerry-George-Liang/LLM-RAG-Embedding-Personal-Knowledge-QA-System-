package com.aiplus.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class RetrieverConfig {

    @Value("${rag.retrieval.top-k:5}")
    private int topK;

    @Value("${rag.retrieval.min-score:0.25}")
    private double minScore;

    @Bean
    public RagRetriever ragRetriever(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        return new RagRetriever(embeddingModel, embeddingStore, topK, minScore);
    }

    public static class RagRetriever {

        private final EmbeddingModel embeddingModel;
        private final EmbeddingStore<TextSegment> embeddingStore;
        private final int maxResults;
        private final double minScore;

        public RagRetriever(EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore,
                           int maxResults, double minScore) {
            this.embeddingModel = embeddingModel;
            this.embeddingStore = embeddingStore;
            this.maxResults = maxResults;
            this.minScore = minScore;
        }

        public List<TextSegment> retrieve(String queryText) {
            List<String> queries = buildQueryVariants(queryText);

            Map<String, EmbeddingMatch<TextSegment>> dedupedMatches = new LinkedHashMap<>();

            for (String query : queries) {
                Embedding queryEmbedding = embeddingModel.embed(query).content();

                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(minScore)
                        .build();

                EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

                for (EmbeddingMatch<TextSegment> match : result.matches()) {
                    String segmentText = match.embedded().text();
                    dedupedMatches.merge(segmentText, match, (existing, incoming) ->
                            incoming.score() > existing.score() ? incoming : existing);
                }
            }

            List<EmbeddingMatch<TextSegment>> allMatches = new ArrayList<>(dedupedMatches.values());
            allMatches.sort((a, b) -> Double.compare(b.score(), a.score()));

            if (allMatches.size() > maxResults) {
                allMatches = allMatches.subList(0, maxResults);
            }

            if (allMatches.isEmpty()) {
                log.warn("检索结果为空: query={}, minScore={}", truncate(queryText, 50), minScore);
            } else {
                double topScore = allMatches.get(0).score();
                double bottomScore = allMatches.get(allMatches.size() - 1).score();
                log.info("检索到 {} 个结果 (查询变体数: {}), 最高分={}, 最低分={}",
                        allMatches.size(), queries.size(),
                        String.format("%.4f", topScore), String.format("%.4f", bottomScore));
                for (int i = 0; i < Math.min(3, allMatches.size()); i++) {
                    EmbeddingMatch<TextSegment> m = allMatches.get(i);
                    log.debug("  结果[{}]: score={}, text={}", i,
                            String.format("%.4f", m.score()),
                            truncate(m.embedded().text(), 80));
                }
            }

            return allMatches.stream()
                    .map(EmbeddingMatch::embedded)
                    .collect(Collectors.toList());
        }

        private List<String> buildQueryVariants(String originalQuery) {
            List<String> variants = new ArrayList<>();
            variants.add(originalQuery);

            if (originalQuery.length() <= 10) {
                variants.add("关于" + originalQuery + "的知识");
                variants.add("什么是" + originalQuery);
                variants.add(originalQuery + "相关信息");
            }

            if (originalQuery.length() > 10 && !originalQuery.contains("？") && !originalQuery.contains("?")) {
                variants.add(originalQuery + "是什么");
            }

            return variants;
        }

        private String truncate(String str, int maxLen) {
            if (str == null) return "";
            return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
        }
    }
}
