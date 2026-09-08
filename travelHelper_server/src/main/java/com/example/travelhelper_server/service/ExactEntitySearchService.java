package com.example.travelhelper_server.service;

import com.example.travelhelper_server.extraction.EntityNameNormalizer;
import com.example.travelhelper_server.extraction.GraphEntityType;
import com.example.travelhelper_server.graph.Neo4jClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.Normalizer;

/**
 * 轻量实体精确召回：直接利用 Neo4j 中已发布实体的名称、标准名、别名和 canonicalKey，
 * 不依赖 embedding。它只接受用户问题中明确出现的实体词，不做模糊猜测。
 */
@Service
@RequiredArgsConstructor
public class ExactEntitySearchService {
    private static final List<String> SEARCHABLE_LABELS =
            List.of("POI", "Food", "Area", "Exhibition", "Station");

    private final Neo4jClient neo4j;
    private final ObjectMapper objectMapper;
    private final EntityNameNormalizer normalizer;

    public List<Map<String, Object>> search(String query, String city, String type, int limit) {
        if (query == null || query.isBlank() || limit <= 0) return List.of();
        return match(query, city, type, loadEntities(), limit);
    }

    private List<EntityRecord> loadEntities() {
        try (Session session = neo4j.session()) {
            Result result = session.run("""
                    MATCH (n)
                    WHERE n.entityId IS NOT NULL
                      AND any(label IN labels(n) WHERE label IN $labels)
                      AND coalesce(n.reviewStatus, 'ACTIVE') = 'ACTIVE'
                    RETURN n.entityId AS entityId,
                           coalesce(n.name, '') AS name,
                           coalesce(n.canonicalName, n.name, '') AS canonicalName,
                           coalesce(n.aliasesJson, '[]') AS aliasesJson,
                           coalesce(n.canonicalKey, '') AS canonicalKey,
                           coalesce(n.city, '') AS city,
                           coalesce(n.tags, []) AS tags,
                           coalesce(n.description, '') AS description,
                           labels(n) AS labels
                    LIMIT 2000
                    """, Map.of("labels", SEARCHABLE_LABELS));
            return result.list(this::toRecord);
        }
    }

    private EntityRecord toRecord(Record record) {
        List<String> labels = record.get("labels").asList(value -> value.asString());
        String type = labels.stream().filter(SEARCHABLE_LABELS::contains).findFirst().orElse("");
        return new EntityRecord(
                record.get("entityId").asString(""),
                record.get("name").asString(""),
                record.get("canonicalName").asString(""),
                aliases(record.get("aliasesJson").asString("[]")),
                record.get("canonicalKey").asString(""),
                record.get("city").asString(""),
                type.toLowerCase(Locale.ROOT),
                record.get("tags").asList(value -> value.asString()),
                record.get("description").asString(""));
    }

    private List<String> aliases(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    List<Map<String, Object>> match(String query, String city, String type,
                                    List<EntityRecord> entities, int limit) {
        List<ExactMatch> matches = new ArrayList<>();
        for (EntityRecord entity : entities) {
            GraphEntityType entityType = parseType(entity.type());
            if (entityType == null || !typeMatches(type, entity.type()) || !cityMatches(city, entity.city())) continue;
            String normalizedQuery = normalizer.canonicalName(entityType, query);
            MatchField field = matchedField(query, normalizedQuery, entityType, entity);
            if (field == null) continue;
            int matchedLength = normalizedValue(entityType, field.value()).length();
            if (matchedLength < 2) continue;
            matches.add(new ExactMatch(entity, field.kind(), matchedLength));
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(ExactMatch::matchedLength).reversed()
                        .thenComparing(match -> match.entity().name()))
                .limit(limit)
                .map(this::toHit)
                .toList();
    }

    private MatchField matchedField(String rawQuery, String normalizedQuery,
                                    GraphEntityType type, EntityRecord entity) {
        String literalQuery = literal(rawQuery);
        for (String alias : entity.aliases()) {
            if (contains(literalQuery, literal(alias))) return new MatchField("ALIAS", alias);
        }
        if (contains(normalizedQuery, normalizedValue(type, entity.name()))) {
            return new MatchField("NAME", entity.name());
        }
        if (contains(normalizedQuery, normalizedValue(type, entity.canonicalName()))) {
            return new MatchField("CANONICAL_NAME", entity.canonicalName());
        }
        String keyName = canonicalKeyName(entity.canonicalKey());
        if (contains(normalizedQuery, normalizedValue(type, keyName))) {
            return new MatchField("CANONICAL_KEY", keyName);
        }
        return null;
    }

    private boolean contains(String query, String candidate) {
        return !candidate.isBlank() && query.contains(candidate);
    }

    private String normalizedValue(GraphEntityType type, String value) {
        return normalizer.canonicalName(type, value);
    }

    private String literal(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\s·•・,，。.!！?？()（）]+", "").toLowerCase(Locale.ROOT);
    }

    private boolean cityMatches(String expected, String actual) {
        if (expected == null || expected.isBlank()) return true;
        String left = normalizer.canonicalName(GraphEntityType.CITY, expected);
        String right = normalizer.canonicalName(GraphEntityType.CITY, actual);
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private boolean typeMatches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equalsIgnoreCase(actual);
    }

    private GraphEntityType parseType(String type) {
        try {
            return GraphEntityType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String canonicalKeyName(String key) {
        int separator = key == null ? -1 : key.lastIndexOf('|');
        return separator < 0 ? "" : key.substring(separator + 1);
    }

    private Map<String, Object> toHit(ExactMatch match) {
        EntityRecord entity = match.entity();
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("id", entity.entityId());
        hit.put("entityId", entity.entityId());
        hit.put("name", entity.canonicalName().isBlank() ? entity.name() : entity.canonicalName());
        hit.put("canonicalName", entity.canonicalName());
        hit.put("aliases", entity.aliases());
        hit.put("city", entity.city());
        hit.put("type", entity.type());
        hit.put("tags", entity.tags());
        hit.put("description", entity.description());
        hit.put("_score", 1.0D);
        hit.put("_exactMatch", true);
        hit.put("_matchType", match.kind());
        return hit;
    }

    record EntityRecord(String entityId, String name, String canonicalName, List<String> aliases,
                        String canonicalKey, String city, String type, List<String> tags,
                        String description) {
    }

    private record MatchField(String kind, String value) {
    }

    private record ExactMatch(EntityRecord entity, String kind, int matchedLength) {
    }
}
