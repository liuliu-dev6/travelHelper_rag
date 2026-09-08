package com.example.travelhelper_server.service;

import com.example.travelhelper_server.utils.LLMUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;

@Service
public class RerankService {
    private static final String PROMPT = """
            你是旅游RAG检索精排器，只判断候选资料与问题的相关性，不回答问题。
            用户明确指定实体时，只保留该实体或与其有明确关系的资料；同城但实体不同不算相关。
            优先保留直接回答问题、城市一致、实体明确且内容具体的资料，去除重复或仅有弱关联的资料。
            只输出合法JSON：{"ids":["候选ID"]}，按相关性从高到低，最多输出指定数量。
            候选ID必须来自输入，不得创造ID。
            """;

    private final LLMUtils llmUtils;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long timeoutMs;

    public RerankService(LLMUtils llmUtils, ObjectMapper objectMapper,
                         @Value("${rag.rerank.enabled:true}") boolean enabled,
                         @Value("${rag.rerank.timeout-ms:6000}") long timeoutMs) {
        this.llmUtils = llmUtils;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
    }

    public RerankResult rerank(String query, List<Map<String, Object>> candidates, int minResults, int maxResults) {
        if (candidates == null || candidates.isEmpty()) return new RerankResult(List.of(), "EMPTY");
        int limit = Math.max(1, Math.min(maxResults, candidates.size()));
        List<Map<String, Object>> local = localRank(query, candidates);
        if (singleEvidenceScope(local)) {
            return new RerankResult(mark(local.subList(0, limit), false), "JAVA_ENTITY_SCOPED");
        }
        if (!enabled || local.size() == 1) {
            return new RerankResult(mark(local.subList(0, limit), false), "JAVA_FALLBACK");
        }
        try {
            String response = llmUtils.chat(PROMPT, prompt(query, local, limit), 0.0, timeoutMs);
            List<String> ids = parseIds(response, local);
            if (ids.isEmpty()) throw new IllegalArgumentException("精排结果为空");
            Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
            local.forEach(hit -> byId.put(id(hit), hit));
            List<Map<String, Object>> selected = new ArrayList<>();
            for (String id : ids) {
                Map<String, Object> hit = byId.remove(id);
                if (hit != null && selected.size() < limit) selected.add(hit);
            }
            int minimum = Math.min(Math.max(1, minResults), limit);
            for (Map<String, Object> hit : local) {
                if (selected.size() >= minimum) break;
                if (byId.remove(id(hit)) != null) selected.add(hit);
            }
            return new RerankResult(mark(selected, true), "LLM_RERANK");
        } catch (Exception ignored) {
            return new RerankResult(mark(local.subList(0, limit), false), "JAVA_FALLBACK");
        }
    }

    private List<Map<String, Object>> localRank(String query, List<Map<String, Object>> candidates) {
        Set<String> queryTokens = tokens(query);
        String normalizedQuery = normalize(query);
        Set<String> explicitlyMentionedNames = candidates.stream()
                .map(hit -> normalize(Objects.toString(hit.get("name"), "")))
                .filter(name -> name.length() >= 3 && normalizedQuery.contains(name))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return candidates.stream().map(source -> {
                    Map<String, Object> hit = new LinkedHashMap<>(source);
                    String text = Objects.toString(hit.get("name"), "") + " "
                            + Objects.toString(hit.get("description"), "") + " "
                            + Objects.toString(hit.get("content"), "");
                    Set<String> candidateTokens = tokens(text);
                    long overlap = queryTokens.stream().filter(candidateTokens::contains).count();
                    double lexical = queryTokens.isEmpty() ? 0 : (double) overlap / queryTokens.size();
                    double vector = clamp(number(hit.get("_score")));
                    double bm25 = number(hit.get("_bm25Score"));
                    double bm25Normalized = bm25 <= 0 ? 0 : bm25 / (bm25 + 1);
                    double exact = Boolean.TRUE.equals(hit.get("_exactMatch")) ? 1 : number(hit.get("_exactBoost"));
                    String candidateName = normalize(Objects.toString(hit.get("name"), ""));
                    double entityAlignment = explicitlyMentionedNames.contains(candidateName) ? .35
                            : explicitlyMentionedNames.isEmpty() ? 0 : -.25;
                    double score = Math.max(0, Math.min(1,
                            exact + vector * .55 + bm25Normalized * .15 + lexical * .20 + entityAlignment));
                    hit.put("_rerankScore", score);
                    return hit;
                }).sorted(Comparator.comparingDouble(hit -> -number(hit.get("_rerankScore"))))
                .toList();
    }

    private boolean singleEvidenceScope(List<Map<String, Object>> candidates) {
        if (candidates.isEmpty()) return false;
        Set<String> scopes = candidates.stream()
                .map(hit -> Objects.toString(hit.get("sourceId"), ""))
                .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toSet());
        return scopes.size() == 1 && candidates.stream()
                .allMatch(hit -> scopes.contains(Objects.toString(hit.get("sourceId"), "")));
    }

    private List<Map<String, Object>> mark(List<Map<String, Object>> values, boolean llm) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> hit = new LinkedHashMap<>(values.get(index));
            if (llm) hit.put("_rerankScore", Math.max(number(hit.get("_rerankScore")), 1D - index * .08));
            hit.put("_rerankRank", index + 1);
            result.add(hit);
        }
        return result;
    }

    private String prompt(String query, List<Map<String, Object>> candidates, int limit) throws Exception {
        List<Map<String, Object>> compact = candidates.stream().limit(15).map(hit -> Map.<String, Object>of(
                "id", id(hit), "title", Objects.toString(hit.get("name"), ""),
                "city", Objects.toString(hit.get("city"), ""),
                "content", abbreviate(Objects.toString(hit.getOrDefault("content", hit.get("description")), ""), 700)
        )).toList();
        return "问题：" + query + "\n最多保留：" + limit + "\n候选：" + objectMapper.writeValueAsString(compact);
    }

    private List<String> parseIds(String response, List<Map<String, Object>> candidates) throws Exception {
        int start = response == null ? -1 : response.indexOf('{');
        int end = response == null ? -1 : response.lastIndexOf('}');
        if (start < 0 || end <= start) return List.of();
        Set<String> allowed = new HashSet<>();
        candidates.forEach(hit -> allowed.add(id(hit)));
        JsonNode ids = objectMapper.readTree(response.substring(start, end + 1)).path("ids");
        if (!ids.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        ids.forEach(value -> { if (value.isTextual() && allowed.contains(value.asText())) result.add(value.asText()); });
        return result.stream().distinct().toList();
    }

    private Set<String> tokens(String value) {
        String text = normalize(value);
        Set<String> result = new LinkedHashSet<>();
        int[] chars = text.codePoints().toArray();
        for (int i = 0; i < chars.length - 1; i++) result.add(new String(chars, i, 2));
        return result;
    }

    private String normalize(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKC)
                .replaceAll("[\\s·•・—–_,，。.!！?？()（）]+", "").toLowerCase(Locale.ROOT);
    }

    private String id(Map<String, Object> hit) {
        return Objects.toString(hit.getOrDefault("entityId", hit.getOrDefault("id", "")), "");
    }
    private double number(Object value) { return value instanceof Number n ? n.doubleValue() : 0; }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }

    public record RerankResult(List<Map<String, Object>> hits, String method) {}
}
