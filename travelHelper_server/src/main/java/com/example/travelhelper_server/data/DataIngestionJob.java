package com.example.travelhelper_server.data;

import com.example.travelhelper_server.client.EmbeddingClient;
import com.example.travelhelper_server.graph.Neo4jClient;
import com.example.travelhelper_server.service.KnowledgeGraphService;
import com.example.travelhelper_server.vector.QdrantClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 启动时灌入种子数据：读 poi_seed.json → embedding → Qdrant + Neo4j。
 * 非致命：灌入失败仅打印告警，不阻断应用启动。
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class DataIngestionJob implements CommandLineRunner {

    private static final int EMBED_BATCH = 16;
    private static final int UPSERT_BATCH = 64;

    @Value("${data.ingest.enabled:true}")
    private boolean enabled;

    @Value("${data.ingest.file:classpath:data/poi_seed.json}")
    private Resource resource;

    @Value("${data.ingest.refresh-vectors:false}")
    private boolean refreshVectors;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;
    private final Neo4jClient neo4jClient;
    private final KnowledgeGraphService knowledgeGraphService;

    @Override
    public void run(String... args) {
        if (!enabled) {
            System.out.println("[data] 灌入已禁用（data.ingest.enabled=false）");
            return;
        }
        try {
            List<SeedPoi> pois = objectMapper.readValue(
                    resource.getInputStream(), new TypeReference<List<SeedPoi>>() {});
            System.out.println("[data] 读取种子数据 " + pois.size() + " 条");

            boolean vectorsReady = qdrantClient.collectionExists() && qdrantClient.countPoints() > 0;
            if (vectorsReady && !refreshVectors) {
                System.out.println("[data] Qdrant 已有数据，跳过向量写入，继续校验 Neo4j");
            } else {
                if (vectorsReady) {
                    System.out.println("[data] 已启用向量刷新：仅覆盖种子实体，保留人工发布知识");
                }
                List<String> texts = pois.stream().map(this::buildEmbedText).toList();
                List<List<Float>> vectors = new ArrayList<>();
                for (int i = 0; i < texts.size(); i += EMBED_BATCH) {
                    List<String> batch = texts.subList(i, Math.min(i + EMBED_BATCH, texts.size()));
                    vectors.addAll(embeddingClient.embedBatch(batch));
                    System.out.println("[data] embedded " + Math.min(i + EMBED_BATCH, texts.size()) + "/" + texts.size());
                }
                qdrantClient.ensureCollection(vectors.get(0).size());
                List<QdrantClient.Point> points = new ArrayList<>();
                for (int i = 0; i < pois.size(); i++) {
                    SeedPoi p = pois.get(i);
                    points.add(new QdrantClient.Point(uuidOf(p.id()), vectors.get(i), payloadOf(p)));
                }
                for (int i = 0; i < points.size(); i += UPSERT_BATCH) {
                    qdrantClient.upsert(points.subList(i, Math.min(i + UPSERT_BATCH, points.size())));
                }
            }

            // Neo4j 总是执行幂等校验和合并，避免 Qdrant 已有数据时跳过图谱修复。
            neo4jClient.verify();
            knowledgeGraphService.initSchema();
            knowledgeGraphService.upsertAll(pois);

            System.out.println("[data] 灌入完成：" + pois.size() + " 条 → Qdrant + Neo4j");
        } catch (Exception e) {
            System.err.println("[data] 灌入失败（请确认 Docker 中 Qdrant/Neo4j 已启动、embedding-model 可用）：" + e.getMessage());
        }
    }

    private String buildEmbedText(SeedPoi p) {
        String tagStr = p.tags() == null ? "" : String.join(" ", p.tags());
        return p.name() + "，" + p.city() + "，" + p.description() + " 标签：" + tagStr;
    }

    private Map<String, Object> payloadOf(SeedPoi p) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", p.id());
        payload.put("entityId", p.id());
        payload.put("name", p.name());
        payload.put("canonicalName", p.name());
        payload.put("aliases", p.aliases() == null ? List.of() : p.aliases());
        payload.put("city", p.city());
        payload.put("type", p.type());
        payload.put("tags", p.tags());
        payload.put("ticket", p.ticket());
        payload.put("rating", p.rating());
        payload.put("description", p.description());
        payload.putAll(embeddingClient.metadata());
        return payload;
    }

    /** 用 POI id 生成确定性 UUID（Qdrant 字符串 id 要求 UUID 格式） */
    private String uuidOf(String id) {
        return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
