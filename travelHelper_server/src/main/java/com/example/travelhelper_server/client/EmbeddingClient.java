package com.example.travelhelper_server.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 向量化客户端：调用火山方舟文本或图文 Embeddings 端点。
 * 与 LLMUtils 同源同 key，无需新增服务。
 */
@Component
public class EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final String apikey;
    private final String baseURL;
    private final String model;
    private final EmbeddingApi api;
    private final int dimensions;
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean responseModelLogged = new AtomicBoolean(false);
    private volatile String actualModel = "unknown";

    /** 随资料持久化模型身份；旧库存未记录时不可由维度反推模型。 */
    public Map<String, Object> metadata() {
        return Map.of("embeddingModel", actualModel, "embeddingEndpoint", model,
                "embeddingDimensions", dimensions, "embeddingApi", api.name().toLowerCase(Locale.ROOT));
    }

    public EmbeddingClient(@Value("${llm.api-key}") String apikey,
                           @Value("${llm.embedding-baseURL:${llm.baseURL}}") String baseURL,
                           @Value("${llm.embedding-model}") String model,
                           @Value("${llm.embedding-api:text}") String api,
                           @Value("${llm.embedding-dimensions:2048}") int dimensions) {
        this.apikey = apikey;
        this.baseURL = baseURL.replaceAll("/+$", "");
        this.model = model;
        this.api = EmbeddingApi.from(api);
        if (dimensions != 1024 && dimensions != 2048) {
            throw new IllegalArgumentException("llm.embedding-dimensions 仅支持1024或2048，当前值: " + dimensions);
        }
        this.dimensions = dimensions;
    }

    /** 单条文本向量化 */
    public List<Float> embed(String text) throws Exception {
        List<List<Float>> list = embedBatch(List.of(text));
        return list.isEmpty() ? List.of() : list.get(0);
    }

    /** 批量向量化（减少请求次数，灌数据时用） */
    public List<List<Float>> embedBatch(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (apikey == null || apikey.isBlank()) {
            throw new IllegalStateException("未配置火山方舟 API Key，请设置环境变量 ARK_API_KEY");
        }

        // 图文向量 API 每次最多接收一个 text，因此按条调用；文本向量 API 支持批量。
        if (api == EmbeddingApi.MULTIMODAL) {
            List<List<Float>> result = new ArrayList<>(texts.size());
            for (String text : texts) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("model", model);
                payload.put("input", List.of(Map.of("type", "text", "text", text)));
                payload.put("encoding_format", "float");
                payload.put("dimensions", dimensions);
                result.addAll(execute("/embeddings/multimodal", payload));
            }
            return result;
        }

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", texts);
        payload.put("encoding_format", "float");
        payload.put("dimensions", dimensions);
        return execute("/embeddings", payload);
    }

    private List<List<Float>> execute(String path, Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);

        Request request = new Request.Builder()
                .url(baseURL + path)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apikey)
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                String hint = response.code() == 404
                        ? "；请确认 ARK_EMBEDDING_MODEL 是当前账号可访问的模型 ID 或推理接入点 ID"
                        : response.code() == 400 && responseBody.contains("does not support this api")
                        ? "；模型与 API 不匹配，请检查 ARK_EMBEDDING_API（text/multimodal）"
                        : "";
                throw new RuntimeException("Embedding 调用失败: " + response.code() + " " + responseBody + hint);
            }
            if (response.body() == null) {
                throw new RuntimeException("Embedding 调用失败: 响应体为空");
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            actualModel = root.path("model").asText("unknown");
            List<List<Float>> embeddings = parseEmbeddings(root);
            embeddings.forEach(vector -> com.example.travelhelper_server.vector.VectorValidator.validate(vector, dimensions));
            if (responseModelLogged.compareAndSet(false, true)) {
                String actualModel = root.path("model").asText("API未返回模型名");
                int dimensions = embeddings.isEmpty() ? 0 : embeddings.getFirst().size();
                log.info("[embedding-runtime] actualModel={}, actualDimensions={}, api={}",
                        actualModel, dimensions, api.name().toLowerCase(Locale.ROOT));
            }
            return embeddings;
        }
    }

    /** 兼容文本向量 API 的 data 数组，以及图文向量 API 的 data 单对象。 */
    static List<List<Float>> parseEmbeddings(JsonNode root) {
        JsonNode data = root.path("data");
        List<JsonNode> items = new ArrayList<>();
        if (data.isArray()) {
            data.forEach(items::add);
        } else if (data.isObject()) {
            items.add(data);
        } else {
            throw new RuntimeException("Embedding 调用失败: 响应中没有 data");
        }
        items.sort(Comparator.comparingInt(item -> item.path("index").asInt(0)));

        List<List<Float>> result = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            JsonNode embedding = item.path("embedding");
            // 部分图文向量响应会把 dense vector 再包一层数组。
            if (embedding.isArray() && embedding.size() == 1 && embedding.get(0).isArray()) {
                embedding = embedding.get(0);
            }

            List<Float> vector = new ArrayList<>();
            if (embedding.isArray()) {
                for (JsonNode value : embedding) {
                    if (!value.isNumber()) throw new IllegalArgumentException("Embedding包含非数字元素");
                    vector.add((float) value.asDouble());
                }
            }
            if (vector.isEmpty()) {
                throw new RuntimeException("Embedding 调用失败: 返回向量为空");
            }
            com.example.travelhelper_server.vector.VectorValidator.validate(vector, 0);
            result.add(vector);
        }
        return result;
    }

    private enum EmbeddingApi {
        TEXT,
        MULTIMODAL;

        static EmbeddingApi from(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "llm.embedding-api 仅支持 text 或 multimodal，当前值: " + value, e);
            }
        }
    }
}
