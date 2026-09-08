package com.example.travelhelper_server.service;

import com.example.travelhelper_server.vector.QdrantClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 基于Qdrant payload构建的轻量中文BM25关键词索引，不引入额外搜索服务。 */
@Service
public class Bm25SearchService {
    private static final Pattern TOKEN_SEGMENT = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private final QdrantClient qdrantClient;

    @Value("${rag.bm25.enabled:true}")
    private boolean enabled = true;
    @Value("${rag.bm25.cache-ttl-ms:30000}")
    private long cacheTtlMs = 30_000;
    @Value("${rag.bm25.k1:1.2}")
    private double k1 = 1.2;
    @Value("${rag.bm25.b:0.75}")
    private double b = 0.75;

    private volatile IndexSnapshot snapshot;

    public Bm25SearchService(QdrantClient qdrantClient) {
        this.qdrantClient = qdrantClient;
    }

    public List<Map<String, Object>> search(String query, int limit, String city, String type) throws Exception {
        return search(query, limit, city, type, null);
    }

    public List<Map<String, Object>> search(String query, int limit, String city, String type,
                                            String sourceId) throws Exception {
        return search(query, limit, city, type, sourceId, false);
    }

    /** 抽象推荐专用：城市已作过滤条件，不能再作为唯一相关性证据。 */
    public List<Map<String, Object>> search(String query, int limit, String city, String type,
                                           String sourceId, boolean requireEffectiveTerms) throws Exception {
        if (!enabled || query == null || query.isBlank() || limit <= 0) return List.of();
        List<String> queryTokens = requireEffectiveTerms ? effectiveTokens(query, city) : tokenize(query);
        if (queryTokens.isEmpty()) return List.of();
        List<Document> documents = index().documents().stream()
                .filter(document -> matchesCity(city, document.city()) && matchesType(type, document)
                        && matchesSource(sourceId, document))
                .toList();
        if (documents.isEmpty()) return List.of();

        Map<String, Integer> documentFrequency = new HashMap<>();
        queryTokens.stream().distinct().forEach(token -> documentFrequency.put(token,
                (int) documents.stream().filter(document -> document.termFrequency().containsKey(token)).count()));
        double averageLength = documents.stream().mapToInt(Document::length).average().orElse(1);
        int corpusSize = documents.size();

        return documents.stream()
                .map(document -> new ScoredDocument(document,
                        score(document, queryTokens, documentFrequency, corpusSize, averageLength)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed()
                        .thenComparing(item -> item.document().id()))
                .limit(limit)
                .map(item -> {
                    Map<String, Object> hit = toHit(item);
                    if (requireEffectiveTerms) {
                        List<String> matched = queryTokens.stream().distinct()
                                .filter(item.document().termFrequency()::containsKey).toList();
                        hit.put("_bm25MatchedTerms", matched);
                        hit.put("_bm25EvidenceApplied", true);
                        hit.put("_bm25EffectiveCoverage", (double) matched.size() / queryTokens.stream().distinct().count());
                    }
                    return hit;
                })
                .toList();
    }

    private static final Set<String> GENERIC_TERMS = Set.of(
            "旅游", "旅行", "景点", "景区", "推荐", "游玩", "地方", "哪里", "什么", "哪些",
            "一下", "一个", "一些", "我们", "我想", "想去", "想看", "看看", "希望", "可以",
            "能够", "适合", "有没有", "有没", "没有", "的地", "当地", "这里", "那里");

    List<String> effectiveTokens(String query, String city) {
        String text = normalizeFilter(query);
        String normalizedCity = normalizeFilter(city);
        if (!normalizedCity.isBlank()) text = text.replace(normalizedCity, " ");
        Set<String> cityTokens = Set.copyOf(tokenize(normalizedCity));
        return tokenize(text).stream().distinct()
                .filter(token -> token.codePointCount(0, token.length()) >= 2)
                .filter(token -> !cityTokens.contains(token) && !GENERIC_TERMS.contains(token)).toList();
    }

    public void invalidate() {
        snapshot = null;
    }

    private IndexSnapshot index() throws Exception {
        IndexSnapshot current = snapshot;
        long now = System.currentTimeMillis();
        if (current != null && now - current.loadedAt() < cacheTtlMs) return current;
        synchronized (this) {
            current = snapshot;
            if (current != null && now - current.loadedAt() < cacheTtlMs) return current;
            List<Document> documents = qdrantClient.scrollAllPayloads().stream().map(this::document).toList();
            snapshot = new IndexSnapshot(documents, now);
            return snapshot;
        }
    }

    private Document document(Map<String, Object> payload) {
        Map<String, Integer> frequencies = new HashMap<>();
        add(frequencies, payload.get("name"), 3);
        if (!Objects.equals(payload.get("name"), payload.get("canonicalName"))) {
            add(frequencies, payload.get("canonicalName"), 3);
        }
        add(frequencies, payload.get("aliases"), 3);
        add(frequencies, payload.get("city"), 1);
        add(frequencies, payload.get("tags"), 2);
        add(frequencies, payload.get("description"), 1);
        add(frequencies, payload.get("content"), 1);
        int length = frequencies.values().stream().mapToInt(Integer::intValue).sum();
        return new Document(entityId(payload), String.valueOf(payload.getOrDefault("city", "")),
                String.valueOf(payload.getOrDefault("type", "")),
                String.valueOf(payload.getOrDefault("knowledgeType", "")), new LinkedHashMap<>(payload),
                frequencies, Math.max(1, length));
    }

    private void add(Map<String, Integer> frequencies, Object value, int weight) {
        if (value instanceof Iterable<?> values) {
            values.forEach(item -> add(frequencies, item, weight));
            return;
        }
        if (value == null) return;
        for (String token : tokenize(String.valueOf(value))) frequencies.merge(token, weight, Integer::sum);
    }

    List<String> tokenize(String value) {
        String normalized = Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        Matcher matcher = TOKEN_SEGMENT.matcher(normalized);
        while (matcher.find()) {
            String segment = matcher.group();
            if (segment.codePoints().allMatch(codePoint -> Character.UnicodeScript.of(codePoint)
                    == Character.UnicodeScript.HAN)) {
                int[] chars = segment.codePoints().toArray();
                if (chars.length == 1) result.add(segment);
                for (int index = 0; index < chars.length - 1; index++) {
                    result.add(new String(chars, index, 2));
                }
            } else {
                result.add(segment);
            }
        }
        return result;
    }

    private double score(Document document, List<String> queryTokens, Map<String, Integer> df,
                         int corpusSize, double averageLength) {
        double score = 0;
        for (String token : queryTokens.stream().distinct().toList()) {
            int frequency = document.termFrequency().getOrDefault(token, 0);
            if (frequency == 0) continue;
            double idf = Math.log(1 + (corpusSize - df.getOrDefault(token, 0) + 0.5)
                    / (df.getOrDefault(token, 0) + 0.5));
            double denominator = frequency + k1 * (1 - b + b * document.length() / averageLength);
            score += idf * frequency * (k1 + 1) / denominator;
        }
        return score;
    }

    private Map<String, Object> toHit(ScoredDocument item) {
        Map<String, Object> hit = new LinkedHashMap<>(item.document().payload());
        hit.put("_bm25Score", item.score());
        hit.put("_retrievalChannel", "BM25");
        return hit;
    }

    private boolean matchesCity(String expected, String actual) {
        if (expected == null || expected.isBlank()) return true;
        String left = normalizeFilter(expected);
        String right = normalizeFilter(actual);
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private boolean matchesType(String expected, Document document) {
        if (expected == null || expected.isBlank()) return true;
        if (normalizeFilter(expected).equals(normalizeFilter(document.type()))) return true;
        if (!"document_chunk".equals(normalizeFilter(document.type()))) return false;
        String knowledgeType = normalizeFilter(document.knowledgeType());
        return knowledgeType.isBlank() || "guide".equals(knowledgeType) || "culture".equals(knowledgeType)
                || normalizeFilter(expected).equals(knowledgeType)
                || ("poi".equals(normalizeFilter(expected)) && Set.of("notice", "opening", "ticket", "exhibition")
                .contains(knowledgeType));
    }

    private boolean matchesSource(String expected, Document document) {
        if (expected == null || expected.isBlank()) return true;
        return expected.equals(Objects.toString(document.payload().get("sourceId"), ""));
    }

    private String normalizeFilter(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKC)
                .replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String entityId(Map<String, Object> payload) {
        return String.valueOf(payload.getOrDefault("entityId", payload.getOrDefault("id", "")));
    }

    private record Document(String id, String city, String type, String knowledgeType, Map<String, Object> payload,
                            Map<String, Integer> termFrequency, int length) {}
    private record ScoredDocument(Document document, double score) {}
    private record IndexSnapshot(List<Document> documents, long loadedAt) {}
}
