package com.example.travelhelper_server.service;

import com.example.travelhelper_server.data.SeedPoi;
import com.example.travelhelper_server.graph.Neo4jClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Neo4j只保存确定性实体关系；通用文档分块由Qdrant负责检索。 */
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {
    private final Neo4jClient neo4j;
    private final ObjectMapper objectMapper;

    public void initSchema() {
        try (Session session = neo4j.session()) {
            session.run("CREATE CONSTRAINT poi_entity_id IF NOT EXISTS FOR (n:POI) REQUIRE n.entityId IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT food_entity_id IF NOT EXISTS FOR (n:Food) REQUIRE n.entityId IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT city_name IF NOT EXISTS FOR (n:City) REQUIRE n.name IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT theme_name IF NOT EXISTS FOR (n:Theme) REQUIRE n.name IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT area_entity_id IF NOT EXISTS FOR (n:Area) REQUIRE n.entityId IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT exhibition_entity_id IF NOT EXISTS FOR (n:Exhibition) REQUIRE n.entityId IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT station_entity_id IF NOT EXISTS FOR (n:Station) REQUIRE n.entityId IS UNIQUE").consume();
        }
    }

    /** 保留少量基础实体兼容导入；大规模文本知识走DocumentIngestion流水线。 */
    public void upsertAll(List<SeedPoi> items) {
        Map<String, String> foodIds = items.stream().filter(item -> "food".equalsIgnoreCase(item.type()))
                .collect(Collectors.toMap(item -> canonicalKey("food", item.city(), item.name()),
                        SeedPoi::id, (first, ignored) -> first));
        try (Session session = neo4j.session()) {
            for (SeedPoi item : items) {
                if ("food".equalsIgnoreCase(item.type())) upsertFoodNode(session, item);
                else upsertPoiNode(session, item);
            }
            for (SeedPoi item : items) {
                linkThemes(session, item.id(), item.tags());
                if (!"food".equalsIgnoreCase(item.type())) {
                    linkFoodRelations(session, item.id(), item.city(), item.nearbyFood(), foodIds);
                }
            }
            migrateLegacyFoodDuplicates(session);
        }
    }

    public void upsert(SeedPoi item) {
        try (Session session = neo4j.session()) {
            if ("food".equalsIgnoreCase(item.type())) upsertFoodNode(session, item);
            else {
                upsertPoiNode(session, item);
                linkFoodRelations(session, item.id(), item.city(), item.nearbyFood(), Map.of());
            }
            linkThemes(session, item.id(), item.tags());
        }
    }

    private void upsertPoiNode(Session session, SeedPoi item) {
        session.run("""
                MERGE (c:City {name:$city})
                MERGE (n:POI {entityId:$id})
                SET n.id=$id, n.canonicalKey=$canonicalKey, n.name=$name, n.canonicalName=$name,
                    n.aliasesJson=$aliasesJson, n.type='poi', n.tags=$tags, n.city=$city,
                    n.description=$description, n.sourceType='SEED'
                MERGE (n)-[:LOCATED_IN]->(c)
                """, Map.of("id", item.id(), "name", item.name(), "city", item.city(),
                "description", Objects.toString(item.description(), ""),
                "canonicalKey", canonicalKey("poi", item.city(), item.name()), "tags", tags(item),
                "aliasesJson", aliasesJson(item))).consume();
    }

    private void upsertFoodNode(Session session, SeedPoi item) {
        session.run("""
                MERGE (c:City {name:$city})
                MERGE (n:Food {entityId:$id})
                SET n.id=$id, n.canonicalKey=$canonicalKey, n.name=$name, n.canonicalName=$name,
                    n.aliasesJson=$aliasesJson, n.type='food', n.tags=$tags, n.city=$city,
                    n.description=$description, n.sourceType='SEED'
                MERGE (n)-[:LOCATED_IN]->(c)
                """, Map.of("id", item.id(), "name", item.name(), "city", item.city(),
                "description", Objects.toString(item.description(), ""),
                "canonicalKey", canonicalKey("food", item.city(), item.name()), "tags", tags(item),
                "aliasesJson", aliasesJson(item))).consume();
    }

    private void linkThemes(Session session, String id, List<String> tags) {
        if (tags == null) return;
        for (String tag : tags) session.run("""
                MERGE (t:Theme {name:$tag})
                WITH t MATCH (n {entityId:$id})
                MERGE (n)-[:HAS_THEME]->(t)
                """, Map.of("tag", tag, "id", id)).consume();
    }

    private void linkFoodRelations(Session session, String poiId, String city, List<String> foods,
                                   Map<String, String> knownFoodIds) {
        if (foods == null) return;
        for (String food : foods) {
            String key = canonicalKey("food", city, food);
            String foodId = knownFoodIds.getOrDefault(key, generatedEntityId("food", key));
            session.run("""
                    MERGE (f:Food {entityId:$foodId})
                    ON CREATE SET f.id=$foodId, f.name=$food, f.city=$city,
                                  f.canonicalKey=$canonicalKey, f.sourceType='SEED_REFERENCE'
                    WITH f MATCH (p:POI {entityId:$poiId})
                    MERGE (p)-[:HAS_FOOD]->(f)
                    """, Map.of("food", food, "foodId", foodId, "city", city,
                    "canonicalKey", key, "poiId", poiId)).consume();
        }
    }

    private void migrateLegacyFoodDuplicates(Session session) {
        session.run("MATCH (n:POI) WHERE n.entityId IS NULL AND n.id IS NOT NULL SET n.entityId=n.id").consume();
        session.run("MATCH (n:Food) WHERE n.entityId IS NULL AND n.id IS NOT NULL SET n.entityId=n.id").consume();
        session.run("""
                MATCH (p:POI)-[legacy:NEARBY]->(f:Food)
                MERGE (p)-[food:HAS_FOOD]->(f)
                ON CREATE SET food.sourceType=coalesce(legacy.sourceType, 'SEED_MIGRATED')
                DELETE legacy
                """).consume();
    }

    public Map<String, Object> queryNeighbors(String entityId) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try (Session session = neo4j.session()) {
            Result centerResult = session.run(
                    "MATCH (n) WHERE n.entityId=$id OR n.id=$id RETURN n LIMIT 1", Map.of("id", entityId));
            if (centerResult.hasNext()) {
                Node center = centerResult.next().get("n").asNode();
                addNode(nodes, seen, entityId, center.get("name").asString(entityId), nodeType(center), nodeProps(center));
            }
            Result neighbors = session.run("""
                    MATCH (n)-[rel]-(m)
                    WHERE n.entityId=$id OR n.id=$id
                    RETURN type(rel) AS relType, m, startNode(rel) AS sourceNode, endNode(rel) AS targetNode
                    """, Map.of("id", entityId));
            while (neighbors.hasNext()) {
                Record record = neighbors.next();
                Node node = record.get("m").asNode();
                String nodeId = nodeKey(node);
                addNode(nodes, seen, nodeId, node.containsKey("name") ? node.get("name").asString("") : nodeId,
                        nodeType(node), nodeProps(node));
                String source = nodeKey(record.get("sourceNode").asNode());
                String target = nodeKey(record.get("targetNode").asNode());
                String relation = record.get("relType").asString();
                edges.add(Map.of("id", source + ":" + relation + ":" + target,
                        "source", source, "target", target, "label", relation));
            }
        }
        result.put("center", entityId);
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    private void addNode(List<Map<String, Object>> nodes, Set<String> seen, String id,
                         String label, String type, Map<String, Object> props) {
        if (seen.add(id)) nodes.add(Map.of("id", id, "label", label, "type", type, "props", props));
    }

    private Map<String, Object> nodeProps(Node node) {
        Map<String, Object> props = new HashMap<>();
        for (String key : List.of("city", "type", "tags", "sourceType")) {
            if (node.containsKey(key) && !node.get(key).isNull()) props.put(key, node.get(key).asObject());
        }
        return props;
    }

    private String nodeKey(Node node) {
        if (node.containsKey("entityId") && !node.get("entityId").isNull()) return node.get("entityId").asString();
        if (node.containsKey("id") && !node.get("id").isNull()) return node.get("id").asString();
        String name = node.containsKey("name") ? node.get("name").asString("") : "";
        return nodeType(node) + ":" + name;
    }

    private String nodeType(Node node) {
        for (String label : node.labels()) {
            if ("POI".equals(label)) return "poi";
            if ("Food".equals(label)) return "food";
            if ("City".equals(label)) return "city";
            if ("Theme".equals(label)) return "theme";
            if ("Area".equals(label)) return "area";
            if ("Exhibition".equals(label)) return "exhibition";
            if ("Station".equals(label)) return "station";
        }
        return "entity";
    }

    private List<String> tags(SeedPoi item) { return item.tags() == null ? List.of() : item.tags(); }

    private String aliasesJson(SeedPoi item) {
        try {
            return objectMapper.writeValueAsString(item.aliases() == null ? List.of() : item.aliases());
        } catch (Exception error) {
            throw new IllegalArgumentException("种子实体别名序列化失败: " + item.id(), error);
        }
    }

    public static String canonicalKey(String type, String city, String name) {
        return type.toLowerCase(Locale.ROOT) + "|" + Objects.toString(city, "").strip().toLowerCase(Locale.ROOT)
                + "|" + Objects.toString(name, "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public static String generatedEntityId(String type, String canonicalKey) {
        return "auto:" + type.toLowerCase(Locale.ROOT) + ":"
                + UUID.nameUUIDFromBytes(canonicalKey.getBytes(StandardCharsets.UTF_8));
    }
}
