package com.example.travelhelper_server.service;

import com.example.travelhelper_server.client.EmbeddingClient;
import com.example.travelhelper_server.vector.QdrantClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 向量检索服务：query → embedding → Qdrant 语义检索。
 */
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;

    /** 语义检索 top-K，返回 POI payload（含 _score） */
    public List<Map<String, Object>> search(String query, int topK, String city) throws Exception {
        return search(query, topK, city, null);
    }

    public List<Map<String, Object>> search(String query, int topK, String city, String type) throws Exception {
        return search(query, topK, city, type, null);
    }

    public List<Map<String, Object>> search(String query, int topK, String city, String type,
                                            String sourceId) throws Exception {
        List<Float> vec = embeddingClient.embed(query);
        return qdrantClient.search(vec, topK, city, type, sourceId);
    }
}
