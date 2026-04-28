package com.aiplus.rag.service;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class KeywordSearchService {

    private final ConcurrentHashMap<String, TextSegment> segmentRegistry = new ConcurrentHashMap<>();

    public void indexSegment(String segmentId, TextSegment segment) {
        segmentRegistry.put(segmentId, segment);
    }

    public void indexSegments(Map<String, TextSegment> segments) {
        segmentRegistry.putAll(segments);
    }

    public void removeByDocumentId(String documentId) {
        segmentRegistry.entrySet().removeIf(entry -> {
            String sourceFile = entry.getValue().metadata().getString("source_file");
            return sourceFile != null && sourceFile.contains(documentId);
        });
    }

    public void clearAll() {
        segmentRegistry.clear();
    }

    public List<KeywordSearchResult> search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        String normalizedQuery = query.toLowerCase().trim();
        List<String> keywords = extractKeywords(normalizedQuery);

        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<KeywordSearchResult> results = new ArrayList<>();

        for (Map.Entry<String, TextSegment> entry : segmentRegistry.entrySet()) {
            String text = entry.getValue().text().toLowerCase();
            double score = calculateKeywordScore(text, keywords);

            if (score > 0) {
                results.add(new KeywordSearchResult(entry.getValue(), score));
            }
        }

        results.sort((a, b) -> Double.compare(b.score, a.score));

        if (results.size() > maxResults) {
            results = results.subList(0, maxResults);
        }

        log.debug("关键词搜索: query='{}', keywords={}, 命中 {} 条", query, keywords, results.size());

        return results;
    }

    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();

        String[] parts = query.split("[\\s,，。？！?！、；;：:·]+");
        for (String part : parts) {
            if (part.length() >= 2) {
                keywords.add(part);
            }
        }

        if (keywords.isEmpty() && query.length() >= 2) {
            keywords.add(query);
        }

        for (int len = Math.min(query.length(), 4); len >= 2; len--) {
            for (int i = 0; i <= query.length() - len; i++) {
                String sub = query.substring(i, i + len);
                if (!keywords.contains(sub)) {
                    keywords.add(sub);
                }
            }
        }

        return keywords;
    }

    private double calculateKeywordScore(String text, List<String> keywords) {
        double score = 0;
        for (String keyword : keywords) {
            int count = countOccurrences(text, keyword);
            if (count > 0) {
                double weight = Math.min(keyword.length() / 2.0, 3.0);
                score += weight * (1 + Math.log1p(count));
            }
        }
        return score;
    }

    private int countOccurrences(String text, String keyword) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    public int getSegmentCount() {
        return segmentRegistry.size();
    }

    public static class KeywordSearchResult {
        private final TextSegment segment;
        private final double score;

        public KeywordSearchResult(TextSegment segment, double score) {
            this.segment = segment;
            this.score = score;
        }

        public TextSegment segment() { return segment; }
        public double score() { return score; }
    }
}
