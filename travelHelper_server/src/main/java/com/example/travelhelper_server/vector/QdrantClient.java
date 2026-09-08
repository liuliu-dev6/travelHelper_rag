package com.example.travelhelper_server.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant REST 客户端（走 6333 端口，避免引入 gRPC 依赖）。
 * 提供建集合、计数、批量 upsert、向量检索。
 */
@Component
public class QdrantClient {

    private final String baseUrl;
    private final String collection;
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MediaType JSON = MediaType.parse("application/json");

    public QdrantClient(@Value("${qdrant.base-url}") String baseUrl,
                        @Value("${qdrant.collection}") String collection) {
        this.baseUrl = baseUrl;
        this.collection = collection;
    }

    /** 集合是否存在 */
    public boolean collectionExists() throws Exception {
        Request req = new Request.Builder().url(baseUrl + "/collections/" + collection).build();
        try (Response resp = client.newCall(req).execute()) {
            return resp.isSuccessful();
        }
    }

    /** 创建集合（幂等；已存在则忽略报错） */
    public void ensureCollection(int dim) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode vectors = body.putObject("vectors");
        vectors.put("size", dim);
        vectors.put("distance", "Cosine");
        Request req = new Request.Builder()
                .url(baseUrl + "/collections/" + collection)
                .put(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                System.out.println("[qdrant] 集合可能已存在: " + resp.code());
            }
        }
        // 过滤字段建立keyword索引，显式实体/source约束无需在全部向量上扫描。
        ensureKeywordPayloadIndex("city");
        ensureKeywordPayloadIndex("type");
        ensureKeywordPayloadIndex("sourceId");
    }

    private void ensureKeywordPayloadIndex(String fieldName) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("field_name", fieldName);
            body.put("field_schema", "keyword");
            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collection + "/index?wait=true")
                    .put(RequestBody.create(body.toString(), JSON)).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() && response.code() != 409) {
                    System.out.println("[qdrant] payload索引创建失败 field=" + fieldName + " code=" + response.code());
                }
            }
        } catch (Exception error) {
            System.out.println("[qdrant] payload索引暂不可用 field=" + fieldName + ": " + error.getMessage());
        }
    }

    /** 点数量（用于判断是否已灌数据） */
    public long countPoints() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("exact", true);
        Request req = new Request.Builder()
                .url(baseUrl + "/collections/" + collection + "/points/count")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("Qdrant count 失败: " + resp.code());
            }
            JsonNode root = objectMapper.readTree(resp.body().string());
            return root.path("result").path("count").asLong(0);
        }
    }

    /** 只读获取当前向量集合配置，供启动诊断展示，不参与检索流程。 */
    public CollectionInfo collectionInfo() throws Exception {
        Request req = new Request.Builder().url(baseUrl + "/collections/" + collection).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new RuntimeException("Qdrant collection info 失败: " + resp.code());
            }
            JsonNode result = objectMapper.readTree(resp.body().string()).path("result");
            JsonNode vectors = result.path("config").path("params").path("vectors");
            return new CollectionInfo(collection, vectors.path("size").asInt(0),
                    vectors.path("distance").asText("unknown"), result.path("points_count").asLong(0),
                    result.path("status").asText("unknown"));
        }
    }

    /** 有界只读抽检；用于启动告警及评测前拦截零向量库存，不代替全库诊断。 */
    public int verifyVectorSample() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("limit", 8);
        body.put("with_vector", true);
        body.put("with_payload", false);
        int dimensions = collectionInfo().dimensions();
        Request request = new Request.Builder()
                .url(baseUrl + "/collections/" + collection + "/points/scroll")
                .post(RequestBody.create(body.toString(), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("向量健康检查失败: " + response.code());
            JsonNode points = objectMapper.readTree(response.body().string()).path("result").path("points");
            for (JsonNode point : points) {
                List<Float> vector = new ArrayList<>();
                for (JsonNode value : point.path("vector")) {
                    if (!value.isNumber()) throw new IllegalStateException("库存向量含非数字元素");
                    vector.add((float) value.asDouble());
                }
                VectorValidator.validate(vector, dimensions);
            }
            return points.size();
        }
    }

    /** 按确定性 UUID 判断知识点是否已存在。 */
    public boolean pointExists(String pointId) throws Exception {
        Request req = new Request.Builder()
                .url(baseUrl + "/collections/" + collection + "/points/" + pointId)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (resp.code() == 404) return false;
            if (!resp.isSuccessful()) {
                throw new RuntimeException("Qdrant point 查询失败: " + resp.code());
            }
            return true;
        }
    }

    /** 批量 upsert 点 */
    public void upsert(List<Point> points) throws Exception {
        if (points.isEmpty()) return;
        int expectedDimensions = collectionInfo().dimensions();
        points.forEach(point -> VectorValidator.validate(point.vector, expectedDimensions));
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode arr = body.putArray("points");
        for (Point p : points) {
            ObjectNode o = arr.addObject();
            o.put("id", p.id);
            ArrayNode vec = o.putArray("vector");
            for (float v : p.vector) {
                vec.add(v);
            }
            o.set("payload", objectMapper.valueToTree(p.payload));
        }
        Request req = new Request.Builder()
                .url(baseUrl + "/collections/" + collection + "/points?wait=true")
                .put(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("Qdrant upsert 失败: " + resp.code() + " " + resp.body().string());
            }
        }
    }

    /** 按发布版本记录的点 ID 删除；Qdrant 删除接口是幂等的。 */
    public void deletePoints(List<String> pointIds) throws Exception {
        if (pointIds == null || pointIds.isEmpty()) return;
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode points = body.putArray("points");
        pointIds.forEach(points::add);
        Request req = new Request.Builder()
                .url(baseUrl + "/collections/" + collection + "/points/delete?wait=true")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("Qdrant delete 失败: " + resp.code() + " " + resp.body().string());
            }
        }
    }

    /**
     * 向量检索 top-K。
     * @param cityFilter 非空时按 payload.city 精确过滤（可选）
     * @return 每个元素 = payload 字段 + "_score"
     */
    public List<Map<String, Object>> search(List<Float> vector, int limit, String cityFilter) throws Exception {
        return search(vector, limit, cityFilter, null);
    }

    /** 向量检索，并可同时按城市和知识类型过滤。 */
    public List<Map<String, Object>> search(List<Float> vector, int limit, String cityFilter,
                                            String typeFilter) throws Exception {
        return search(vector, limit, cityFilter, typeFilter, null);
    }

    /** sourceId 非空时将显式实体约束下推到向量库，避免同城其他景点进入候选池。 */
    public List<Map<String, Object>> search(List<Float> vector, int limit, String cityFilter,
                                            String typeFilter, String sourceId) throws Exception {
        VectorValidator.validate(vector, 0);
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode vec = body.putArray("vector");
        for (float v : vector) {
            vec.add(v);
        }
        body.put("limit", limit);
        body.put("with_payload", true);

        if (hasText(cityFilter) || hasText(sourceId) || hasText(typeFilter)) {
            ObjectNode filter = body.putObject("filter");
            ArrayNode must = filter.putArray("must");
            addMatch(must, "city", cityFilter);
            addMatch(must, "sourceId", sourceId);
            if (hasText(typeFilter)) {
                // 保留资料分块，防止景点类型过滤误删官方网页和上传文档。
                ObjectNode typeCondition = must.addObject();
                ArrayNode alternatives = typeCondition.putArray("should");
                addMatch(alternatives, "type", typeFilter);
                addMatch(alternatives, "type", "document_chunk");
            }
        }

        Request req = new Request.Builder()
                .url(baseUrl + "/collections/" + collection + "/points/search")
                .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("Qdrant search 失败: " + resp.code() + " " + resp.body().string());
            }
            JsonNode root = objectMapper.readTree(resp.body().string());
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode hit : root.path("result")) {
                Map<String, Object> payload = objectMapper.readValue(
                        hit.path("payload").toString(), Map.class);
                payload.put("_score", hit.path("score").asDouble());
                result.add(payload);
            }
            return result;
        }
    }

    /** 只滚动读取全部 payload，不加载向量；供轻量BM25索引构建使用。 */
    public List<Map<String, Object>> scrollAllPayloads() throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        JsonNode offset = null;
        do {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("limit", 256);
            body.put("with_payload", true);
            body.put("with_vector", false);
            if (offset != null && !offset.isNull()) body.set("offset", offset);
            Request req = new Request.Builder()
                    .url(baseUrl + "/collections/" + collection + "/points/scroll")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    throw new RuntimeException("Qdrant scroll 失败: " + resp.code() + " " + resp.body().string());
                }
                JsonNode page = objectMapper.readTree(resp.body().string()).path("result");
                for (JsonNode point : page.path("points")) {
                    Map<String, Object> payload = objectMapper.readValue(
                            point.path("payload").toString(), Map.class);
                    if (!payload.containsKey("id")) payload.put("id", point.path("id").asText());
                    result.add(payload);
                }
                offset = page.get("next_page_offset");
            }
        } while (offset != null && !offset.isNull());
        return result;
    }

    private void addMatch(ArrayNode must, String key, String value) {
        if (!hasText(value)) return;
        ObjectNode condition = must.addObject();
        condition.put("key", key);
        condition.putObject("match").put("value", value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 一个待写入的点 */
    public static class Point {
        public final String id;
        public final List<Float> vector;
        public final Map<String, Object> payload;

        public Point(String id, List<Float> vector, Map<String, Object> payload) {
            this.id = id;
            this.vector = vector;
            this.payload = payload;
        }
    }

    public record CollectionInfo(String collection, int dimensions, String distance,
                                 long pointsCount, String status) {}
}
