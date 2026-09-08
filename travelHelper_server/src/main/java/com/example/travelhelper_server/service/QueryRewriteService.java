package com.example.travelhelper_server.service;

import com.example.travelhelper_server.intent.IntentType;
import com.example.travelhelper_server.intent.QueryIntent;
import com.example.travelhelper_server.utils.LLMUtils;
import com.example.travelhelper_server.service.UserPreferenceService.PreferenceSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class QueryRewriteService {

    private static final String SYSTEM_PROMPT = """
            你是旅游检索查询改写器，不回答问题。根据原问题、意图和约束生成2个互补的中文检索查询。
            查询1强调目的地和实体，查询2强调主题、同行人和限制。不得增加用户没有表达的城市、预算或事实。
            只输出合法JSON：{"queries":["查询1","查询2"]}。不要输出Markdown。
            """;

    private final LLMUtils llmUtils;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long timeoutMs;

    public QueryRewriteService(LLMUtils llmUtils,
                               ObjectMapper objectMapper,
                               @Value("${rag.query-rewrite.enabled:true}") boolean enabled,
                               @Value("${rag.query-rewrite.timeout-ms:6000}") long timeoutMs) {
        this.llmUtils = llmUtils;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
    }

    public List<String> rewrite(String query, QueryIntent intent, PreferenceSnapshot preference) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(query);
        if (enabled) {
            try {
                String userPrompt = "原问题：" + query + "\n主要意图：" + intent.primaryIntent()
                        + "\n槽位：" + intent.slots() + "\n用户偏好：" + preference;
                String response = llmUtils.chat(SYSTEM_PROMPT, userPrompt, 0.0, timeoutMs);
                JsonNode queries = objectMapper.readTree(extractJson(response)).path("queries");
                if (queries.isArray()) {
                    for (JsonNode item : queries) {
                        if (item.isTextual() && !item.asText().isBlank()) result.add(item.asText().strip());
                        if (result.size() >= 3) break;
                    }
                }
            } catch (Exception ignored) {
                // 下面的确定性改写保证 LLM 不可用时仍能多路召回。
            }
        }
        for (String fallback : deterministicRewrites(query, intent, preference)) {
            if (result.size() >= 3) break;
            result.add(fallback);
        }
        return new ArrayList<>(result);
    }

    private List<String> deterministicRewrites(String query, QueryIntent intent, PreferenceSnapshot preference) {
        List<String> values = new ArrayList<>();
        String city = intent.slots().cities().stream().findFirst().orElse("");
        String themes = String.join(" ", intent.slots().themes());
        String companions = String.join(" ", intent.slots().companions());
        if (intent.primaryIntent() == IntentType.FOOD_RECOMMENDATION) {
            values.add((city + " 特色美食 小吃 " + themes).strip());
        } else if (intent.primaryIntent() == IntentType.ITINERARY_PLANNING) {
            values.add((city + " 必去景点 行程 " + themes + " " + companions).strip());
            values.add((city + " 交通方便 景点组合 " + String.join(" ", intent.slots().constraints())).strip());
        } else {
            values.add((city + " 景点推荐 " + themes + " " + companions).strip());
        }
        if (!preference.preferredThemes().isEmpty()) {
            values.add((city + " " + String.join(" ", preference.preferredThemes()) + " 旅游推荐").strip());
        }
        return values.stream().filter(item -> !item.isBlank() && !item.equals(query)).toList();
    }

    private String extractJson(String value) {
        int start = value == null ? -1 : value.indexOf('{');
        int end = value == null ? -1 : value.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("改写结果不是JSON");
        return value.substring(start, end + 1);
    }
}
