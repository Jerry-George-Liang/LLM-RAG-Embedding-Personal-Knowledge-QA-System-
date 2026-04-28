package com.aiplus.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentSplitService {

    private static final Logger log = LoggerFactory.getLogger(DocumentSplitService.class);

    @Value("${rag.splitting.max-segment-size:500}")
    private int maxSegmentSize;

    @Value("${rag.splitting.overlap:50}")
    private int overlapSize;

    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^(#{1,4}\\s+.+)$", Pattern.MULTILINE);

    public List<TextSegment> split(List<Document> documents, String fileName) {
        List<TextSegment> segments = new ArrayList<>();
        int globalIndex = 0;

        for (Document doc : documents) {
            String text = doc.text();
            if (text == null || text.isBlank()) continue;

            List<String> chunks = splitByStructure(text);

            for (String chunk : chunks) {
                String trimmed = chunk.trim();
                if (trimmed.isEmpty()) continue;
                Metadata meta = new Metadata();
                meta.put("segment_index", String.valueOf(globalIndex));
                meta.put("source_file", fileName);
                segments.add(TextSegment.from(trimmed, meta));
                globalIndex++;
            }
        }

        log.info("文档切分完成: {} -> {} 个段落", documents.size(), segments.size());
        return segments;
    }

    private List<String> splitByStructure(String text) {
        Matcher matcher = HEADER_PATTERN.matcher(text);
        List<Integer> headerPositions = new ArrayList<>();
        List<String> headerTitles = new ArrayList<>();

        while (matcher.find()) {
            headerPositions.add(matcher.start());
            headerTitles.add(matcher.group(1).trim());
        }

        if (!headerPositions.isEmpty() && headerPositions.size() >= 2) {
            return splitByHeaders(text, headerPositions, headerTitles);
        }

        return splitByFixedSize(text);
    }

    private List<String> splitByHeaders(String text, List<Integer> positions, List<String> titles) {
        List<String> chunks = new ArrayList<>();

        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i);
            int end = (i + 1 < positions.size()) ? positions.get(i + 1) : text.length();
            String sectionText = text.substring(start, end).trim();

            if (sectionText.isEmpty()) continue;

            if (sectionText.length() <= maxSegmentSize) {
                chunks.add(sectionText);
            } else {
                String title = titles.get(i);
                List<String> subChunks = splitSectionWithPrefix(sectionText, title);
                chunks.addAll(subChunks);
            }
        }

        if (positions.get(0) > 0) {
            String preamble = text.substring(0, positions.get(0)).trim();
            if (!preamble.isEmpty()) {
                chunks.add(0, preamble);
            }
        }

        return chunks;
    }

    private List<String> splitSectionWithPrefix(String sectionText, String title) {
        String firstLine = sectionText.split("\\n", 2)[0];
        String body = sectionText.substring(firstLine.length()).trim();

        List<String> bodyChunks = splitByFixedSize(body);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < bodyChunks.size(); i++) {
            if (i == 0) {
                result.add(firstLine + "\n" + bodyChunks.get(i));
            } else {
                result.add(title + "（续）\n" + bodyChunks.get(i));
            }
        }

        return result;
    }

    private List<String> splitByFixedSize(String text) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= maxSegmentSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxSegmentSize, text.length());
            if (end < text.length()) {
                int breakPoint = findBreakPoint(text, end);
                end = breakPoint > start ? breakPoint : end;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = end - overlapSize;
            if (start < 0) start = 0;
            if (start >= text.length()) break;
        }
        return chunks;
    }

    private int findBreakPoint(String text, int preferredEnd) {
        for (int i = preferredEnd; i > Math.max(preferredEnd - overlapSize, 0); i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；') {
                return i + 1;
            }
        }
        return preferredEnd;
    }
}
