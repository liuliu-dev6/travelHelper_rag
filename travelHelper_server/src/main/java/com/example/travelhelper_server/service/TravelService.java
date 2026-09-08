package com.example.travelhelper_server.service;

import com.example.travelhelper_server.intent.IntentClassification;
import com.example.travelhelper_server.intent.IntentClassifierService;
import com.example.travelhelper_server.intent.IntentRouter;
import com.example.travelhelper_server.intent.IntentType;
import com.example.travelhelper_server.intent.QueryIntent;
import com.example.travelhelper_server.intent.RoutePlan;
import com.example.travelhelper_server.dto.ConversationTurnDTO;
import com.example.travelhelper_server.service.ConversationContextService.ResolvedConversation;
import com.example.travelhelper_server.service.MultiQueryRetrievalService.RetrievalResult;
import com.example.travelhelper_server.service.UserPreferenceService.PreferenceSnapshot;
import com.example.travelhelper_server.realtime.RealtimeTravelProvider.RealtimeResult;
import com.example.travelhelper_server.realtime.RealtimeTravelService;
import com.example.travelhelper_server.utils.LLMUtils;
import com.example.travelhelper_server.vo.GraphVO;
import com.example.travelhelper_server.vo.MetaVO;
import com.example.travelhelper_server.vo.SourceItemVO;
import com.example.travelhelper_server.vo.SourcesVO;
import com.example.travelhelper_server.vo.StreamChunkVO;
import com.example.travelhelper_server.vo.StreamDoneVO;
import com.example.travelhelper_server.vo.StreamErrorVO;
import com.example.travelhelper_server.vo.ToolCallVO;
import com.example.travelhelper_server.vo.TravelRecommendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


@Service
@RequiredArgsConstructor
public class TravelService {

    private static final List<String> KNOWN_CITIES = List.of(
            "呼和浩特", "乌鲁木齐", "哈尔滨", "石家庄",
            "北京", "上海", "天津", "重庆", "广州", "深圳", "珠海", "佛山", "东莞",
            "长沙", "武汉", "南京", "苏州", "无锡", "杭州", "宁波", "温州", "绍兴",
            "成都", "西安", "昆明", "大理", "丽江", "贵阳", "南宁", "桂林", "海口", "三亚",
            "福州", "厦门", "泉州", "南昌", "合肥", "济南", "青岛", "烟台", "郑州", "洛阳",
            "太原", "沈阳", "大连", "长春", "兰州", "西宁", "银川", "拉萨", "张家界");

    private final KnowledgeGraphService knowledgeGraphService;
    private final IntentClassifierService intentClassifierService;
    private final IntentRouter intentRouter;
    private final ConversationContextService conversationContextService;
    private final MultiQueryRetrievalService multiQueryRetrievalService;
    private final RerankService rerankService;
    private final ParentChunkExpansionService parentChunkExpansionService;
    private final UserPreferenceService userPreferenceService;
    private final RealtimeTravelService realtimeTravelService;
    private final ConversationService conversationService;
    private final LLMUtils llmUtils;
    private final ObjectMapper objectMapper;

    @Value("${rag.candidate-k:15}")
    private int ragCandidateK;

    @Value("${rag.context-min:3}")
    private int ragContextMin;

    @Value("${rag.context-max:5}")
    private int ragContextMax;

    @Value("${rag.min-score:0.35}")
    private double ragMinScore;

    public TravelRecommendVO recommend(String city,Integer days,Double budget){
        String prompt = buildTravelPrompt(city, budget, days);
        try {
            String response=llmUtils.chat(null, prompt);
            return parseTravelResponse(response);
        }
        catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
    // service旅游推荐返回数据处理
    private TravelRecommendVO parseTravelResponse(String response) {
        TravelRecommendVO result = new TravelRecommendVO();

        try {
            String jsonContent = extractJson(response);
            if (jsonContent != null) {
                result = objectMapper.readValue(jsonContent, TravelRecommendVO.class);
            } else {
                result.setSuccess(false);
                result.setError("未能从响应中提取JSON");
                result.setRawResponse(response);
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError("JSON解析失败");
            result.setRawResponse(response);
        }

        return result;
    }

    private String extractJson(String response) {  //"扒开"这些 markdown 外衣，把里面的 JSON 抠出来
        if (response == null || response.isEmpty()) {
            return null;
        }

        String[] patterns = {
                "```json\\n([\\s\\S]*?)\\n```",
                "```\\n([\\s\\S]*?)\\n```"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(response);
            if (m.find()) {
                return m.group(1);
            }
        }

        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        return null;
    }

    private String buildTravelPrompt(String city, Double budget, Integer days) {
        
        return "你是一个专业的旅游规划师，擅长根据用户的需求生成详细的旅行行程。\n\n" +
                "请根据以下信息为用户生成一份详细的旅游规划：\n" +
                "- 目的地城市：" + city + "\n" +
                "- 预算：" + budget + "元\n" +
                "- 旅行天数：" + days + "天\n\n" +
                "要求：\n" +
                "1. 每天的行程安排（上午、下午、晚上）\n" +
                "2. 每个景点的详细介绍\n" +
                "3. 交通建议\n" +
                "4. 预算分配明细\n" +
                "5. 注意事项\n\n" +
                "请以JSON格式输出，结构如下：\n" +
                "{\n" +
                "  \"success\": true,\n" +
                "  \"city\": \"城市名\",\n" +
                "  \"days\": 天数,\n" +
                "  \"totalBudget\": 总预算,\n" +
                "  \"dailyItinerary\": [\n" +
                "    {\n" +
                "      \"day\": 1,\n" +
                "      \"date\": \"第1天\",\n" +
                "      \"morning\": {\n" +
                "        \"spot\": \"景点名称\",\n" +
                "        \"duration\": \"游览时长\",\n" +
                "        \"ticket\": \"门票价格\",\n" +
                "        \"transportation\": \"交通方式\",\n" +
                "        \"description\": \"景点介绍\"\n" +
                "      },\n" +
                "      \"afternoon\": {\n" +
                "        \"spot\": \"景点名称\",\n" +
                "        \"duration\": \"游览时长\",\n" +
                "        \"ticket\": \"门票价格\",\n" +
                "        \"transportation\": \"交通方式\",\n" +
                "        \"description\": \"景点介绍\"\n" +
                "      },\n" +
                "      \"evening\": {\n" +
                "        \"spot\": \"活动名称\",\n" +
                "        \"duration\": \"活动时长\",\n" +
                "        \"ticket\": \"费用\",\n" +
                "        \"transportation\": \"交通方式\",\n" +
                "        \"description\": \"活动介绍\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"budgetBreakdown\": {\n" +
                "    \"accommodation\": 住宿费用,\n" +
                "    \"food\": 餐饮费用,\n" +
                "    \"transportation\": 交通费用,\n" +
                "    \"tickets\": 门票费用,\n" +
                "    \"other\": 其他费用\n" +
                "  },\n" +
                "  \"tips\": [\"提示1\", \"提示2\", \"提示3\"],\n" +
                "  \"warnings\": [\"注意事项1\", \"注意事项2\"]\n" +
                "}\n\n" +
                "请确保JSON格式正确，可以被解析。";
    }
    public SseEmitter chat(String message, List<ConversationTurnDTO> suppliedContext, String username,
                           String conversationId) {
        SseEmitter emitter = new SseEmitter(180000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));

        Thread.ofVirtual().name("rag-chat-" + UUID.randomUUID()).start(() -> {
            long startedAt = System.currentTimeMillis();
            List<SourceItemVO> sources = new ArrayList<>();
            Map<String, Object> graph = Map.of();
            RetrievalResult retrieval = null;
            RealtimeResult realtime = null;

            try {
                String sessionId = "s_" + UUID.randomUUID().toString().substring(0, 8);
                sendEvent(emitter, MetaVO.of(sessionId, conversationId));

                long intentStarted = System.currentTimeMillis();
                sendEvent(emitter, ToolCallVO.start("intent", "intent_analysis", Map.of("query", message)));
                ResolvedConversation conversation = conversationContextService.resolve(message, suppliedContext);
                String effectiveQuery = conversation.effectiveQuery();
                IntentClassification classification = intentClassifierService.classify(effectiveQuery);
                QueryIntent intent = classification.intent();
                RoutePlan route = intentRouter.route(intent);
                PreferenceSnapshot preference = userPreferenceService.get(username);
                String city = intent.slots().cities().stream().findFirst().orElseGet(() -> extractCity(effectiveQuery));
                String intentSummary = intent.primaryIntent() + " · " + intent.complexity()
                        + " · " + sourceLabel(classification.source())
                        + " · " + route.description()
                        + (conversation.inheritedPendingRequest() ? " · 已继承上轮待补信息" : "")
                        + (city == null ? "" : " · 目的地：" + city);
                sendEvent(emitter, ToolCallVO.end("intent", "intent_analysis", intentSummary,
                        elapsed(intentStarted)));

                if (route.clarification() != null) {
                    completeWithMessage(emitter, route.clarification(), startedAt, 0,
                            username, conversationId, Map.of("intent", intent, "route", route));
                    return;
                }
                if (route.realtimeRequired()) {
                    long realtimeStarted = System.currentTimeMillis();
                    String toolName = realtimeTravelService.toolName(intent, effectiveQuery);
                    Map<String, Object> realtimeArgs = new LinkedHashMap<>();
                    realtimeArgs.put("query", effectiveQuery);
                    if (city != null) realtimeArgs.put("city", city);
                    if (intent.slots().dateText() != null) realtimeArgs.put("date", intent.slots().dateText());
                    sendEvent(emitter, ToolCallVO.start("realtime", toolName, realtimeArgs));
                    realtime = realtimeTravelService.query(intent, effectiveQuery, city);
                    if (realtime.available()) {
                        sendEvent(emitter, ToolCallVO.end("realtime", toolName,
                                "已获取" + (city == null ? "目的地" : city) + "实时数据 · " + realtime.source(),
                                elapsed(realtimeStarted)));
                    } else {
                        sendEvent(emitter, ToolCallVO.error("realtime", toolName,
                                realtime.content(), elapsed(realtimeStarted)));
                    }
                }
                if (intent.primaryIntent() == IntentType.REALTIME_INFO) {
                    completeWithMessage(emitter,
                            realtime.available()
                                    ? realtime.content() + "\n数据来源：" + realtime.source() + "，查询时间：" + realtime.observedAt()
                                    : realtime.content(),
                            startedAt, 0, username, conversationId,
                            Map.of("intent", intent, "route", route));
                    return;
                }
                if (intent.primaryIntent() == IntentType.PREFERENCE_MEMORY) {
                    PreferenceSnapshot updated = userPreferenceService.remember(username, intent, effectiveQuery);
                    completeWithMessage(emitter,
                            userPreferenceService.summary(updated),
                            startedAt, 0, username, conversationId,
                            Map.of("intent", intent, "route", route));
                    return;
                }

                if (route.vectorSearch()) {
                    long searchStarted = System.currentTimeMillis();
                    Map<String, Object> searchArgs = new LinkedHashMap<>();
                    String retrievalQuery = userPreferenceService.enrichQuery(effectiveQuery, preference);
                    searchArgs.put("query", effectiveQuery);
                        searchArgs.put("candidateK", ragCandidateK);
                    if (city != null) searchArgs.put("city", city);
                    if (route.typeFilter() != null) searchArgs.put("type", route.typeFilter());
                    sendEvent(emitter, ToolCallVO.start("vector", "search_attractions", searchArgs));
                    try {
                        retrieval = multiQueryRetrievalService.search(
                                retrievalQuery, effectiveQuery, intent, route, city, preference, ragCandidateK);
                        long rerankStarted = System.currentTimeMillis();
                        sendEvent(emitter, ToolCallVO.start("rerank", "rerank_sources",
                                Map.of("candidates", retrieval.hits().size(), "maxResults", ragContextMax)));
                        // 小块负责精排；多取一些候选，父块展开并去重后再限制最终上下文数量。
                        int childRerankLimit = Math.min(retrieval.hits().size(), ragContextMax * 2);
                        RerankService.RerankResult reranked = rerankService.rerank(
                                effectiveQuery, retrieval.hits(), ragContextMin, childRerankLimit);
                        List<Map<String, Object>> expanded = parentChunkExpansionService.expand(
                                reranked.hits(), ragContextMax);
                        sendEvent(emitter, ToolCallVO.end("rerank", "rerank_sources",
                                reranked.method() + " 小块精排并展开为 " + expanded.size() + " 条父级上下文",
                                elapsed(rerankStarted)));
                        sources = expanded.stream()
                                .filter(hit -> scoreOf(hit) >= ragMinScore)
                                .map(this::toSource)
                                .toList();
                        if (!sources.isEmpty()) {
                            sendEvent(emitter, SourcesVO.of(sources));
                        }
                        String retrievalSummary = switch (retrieval.strategy()) {
                            case "EXACT_GRAPH" -> "精确锁定 " + retrieval.exactMatches().size()
                                    + " 个实体，跳过语义召回并转入知识图谱验证";
                            case "VECTOR_ONLY" -> "简单问句使用原问题向量召回 "
                                    + sources.size() + " 条可信来源";
                            case "VECTOR_RRF" -> retrieval.queries().size()
                                    + " 路向量查询经通道内RRF融合，召回 " + sources.size() + " 条可信来源";
                            case "BM25_ONLY" -> "检测到明确城市/主题/属性约束，使用关键词精确召回 "
                                    + sources.size() + " 条可信来源，跳过低收益向量调用";
                            default -> (retrieval.exactMatches().isEmpty() ? "" : "精确命中 "
                                    + retrieval.exactMatches().size() + " 个实体，")
                                    + retrieval.queries().size() + " 路向量查询与 "
                                    + retrieval.bm25Queries().size() + " 路BM25查询经加权RRF融合，召回 "
                                    + sources.size() + " 条可信来源";
                        } + (preference.isEmpty() ? "" : "，已应用用户偏好重排序");
                        sendEvent(emitter, ToolCallVO.end("vector", "search_attractions",
                                retrievalSummary,
                                elapsed(searchStarted)));
                    } catch (Exception retrievalError) {
                        sendEvent(emitter, ToolCallVO.error("vector", "search_attractions",
                                "向量检索暂不可用，已降级为普通对话", elapsed(searchStarted)));
                    }
                }

                SourceItemVO center = null;
                if (route.graphSearch() && retrieval != null) {
                    center = retrieval.exactMatches().isEmpty()
                            ? (sources.isEmpty() || "document_chunk".equalsIgnoreCase(sources.getFirst().type())
                            ? null : sources.getFirst())
                            : toSource(retrieval.exactMatches().getFirst());
                }
                if (center != null) {
                    long graphStarted = System.currentTimeMillis();
                    sendEvent(emitter, ToolCallVO.start("graph", "query_knowledge_graph",
                            Map.of("poiId", center.id(), "title", center.title())));
                    try {
                        graph = knowledgeGraphService.queryNeighbors(center.id());
                        sendEvent(emitter, GraphVO.from(graph));
                        int relatedCount = Math.max(0, graphNodes(graph).size() - 1);
                        sendEvent(emitter, ToolCallVO.end("graph", "query_knowledge_graph",
                                "找到 " + relatedCount + " 个确定性知识关联", elapsed(graphStarted)));
                    } catch (Exception graphError) {
                        sendEvent(emitter, ToolCallVO.error("graph", "query_knowledge_graph",
                                "知识图谱暂不可用，继续使用向量结果", elapsed(graphStarted)));
                        graph = Map.of();
                    }
                }

                String systemPrompt = buildRagSystemPrompt(sources, graph, city, intent, route,
                        conversation, preference, realtime);
                long generateStarted = System.currentTimeMillis();
                sendEvent(emitter, ToolCallVO.start("generate", "generate", Map.of()));
                Consumer<String> callback = content -> {
                    if (closed.get()) return;
                    try {
                        sendEvent(emitter, StreamChunkVO.of(content));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                };
                String fullAnswer = llmUtils.streamChat(systemPrompt, effectiveQuery, callback);
                sendEvent(emitter, ToolCallVO.end("generate", "generate", "回答生成完成",
                        elapsed(generateStarted)));
                conversationService.appendAssistant(username, conversationId, fullAnswer,
                        conversationDetail(sources, graph, intent, route, startedAt));
                sendEvent(emitter, StreamDoneVO.of(elapsed(startedAt), sources.size()));
                emitter.complete();
            } catch (Exception e) {
                try {
                    if (!closed.get()) {
                        sendEvent(emitter, StreamErrorVO.of(rootMessage(e)));
                    }
                } catch (Exception sendError) {
                    System.err.println("发送 SSE 错误事件失败: " + sendError.getMessage());
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void completeWithMessage(SseEmitter emitter, String content, long startedAt, int sourcesCount,
                                     String username, String conversationId,
                                     Map<String, Object> detail)
            throws IOException {
        sendEvent(emitter, StreamChunkVO.of(content));
        conversationService.appendAssistant(username, conversationId, content,
                objectMapper.writeValueAsString(detail));
        sendEvent(emitter, StreamDoneVO.of(elapsed(startedAt), sourcesCount));
        emitter.complete();
    }

    private String conversationDetail(List<SourceItemVO> sources, Map<String, Object> graph,
                                      QueryIntent intent, RoutePlan route, long startedAt) throws IOException {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sources", sources);
        if (graph != null && !graph.isEmpty()) {
            detail.put("graph", graph);
        }
        detail.put("intent", intent);
        detail.put("route", route);
        detail.put("stats", Map.of("durationMs", elapsed(startedAt), "sourcesCount", sources.size()));
        return objectMapper.writeValueAsString(detail);
    }

    private String sourceLabel(IntentClassification.Source source) {
        return switch (source) {
            case LLM -> "LLM分类";
            case LLM_RETRY -> "LLM重试分类";
            case LOCAL_RULES -> "规则降级";
            case SAFE_DEFAULT -> "默认RAG降级";
        };
    }

    private void sendEvent(SseEmitter emitter, Object event) throws IOException {
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(event)));
    }

    static String extractCity(String message) {
        return KNOWN_CITIES.stream().filter(message::contains).findFirst().orElse(null);
    }

    private double scoreOf(Map<String, Object> hit) {
        if (Boolean.TRUE.equals(hit.get("_exactMatch")) || number(hit.get("_exactBoost")) > 0) return 1D;
        double rerankScore = number(hit.get("_rerankScore"));
        if (rerankScore > 0) return rerankScore;
        double vectorScore = number(hit.get("_score"));
        if (vectorScore > 0) return vectorScore;
        double bm25Score = number(hit.get("_bm25Score"));
        // BM25没有固定上界，仅用于来源卡片置信度展示和最低质量过滤。
        return bm25Score <= 0 ? 0D : bm25Score / (bm25Score + 1D);
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    @SuppressWarnings("unchecked")
    private SourceItemVO toSource(Map<String, Object> hit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        copyIfPresent(hit, payload, "ticket");
        copyIfPresent(hit, payload, "rating");
        copyIfPresent(hit, payload, "_exactMatch");
        copyIfPresent(hit, payload, "_matchType");
        copyIfPresent(hit, payload, "_bm25Score");
        copyIfPresent(hit, payload, "_rrfScore");
        copyIfPresent(hit, payload, "_retrievalChannels");
        copyIfPresent(hit, payload, "_rerankScore");
        copyIfPresent(hit, payload, "_rerankRank");
        copyIfPresent(hit, payload, "documentId");
        copyIfPresent(hit, payload, "chunkIndex");
        copyIfPresent(hit, payload, "parentChunkId");
        copyIfPresent(hit, payload, "sectionPath");
        copyIfPresent(hit, payload, "matchedChildContent");
        copyIfPresent(hit, payload, "_contextExpanded");
        copyIfPresent(hit, payload, "sourceUri");
        copyIfPresent(hit, payload, "mimeType");
        return new SourceItemVO(
                String.valueOf(hit.getOrDefault("id", "")),
                String.valueOf(hit.getOrDefault("type", "poi")),
                String.valueOf(hit.getOrDefault("name", "未命名地点")),
                String.valueOf(hit.getOrDefault("city", "")),
                hit.get("tags") instanceof List<?> tags ? (List<String>) tags : List.of(),
                scoreOf(hit),
                String.valueOf(hit.getOrDefault("description", "")),
                payload);
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) target.put(key, source.get(key));
    }

    private String buildRagSystemPrompt(List<SourceItemVO> sources, Map<String, Object> graph,
                                        String city, QueryIntent intent, RoutePlan route,
                                        ResolvedConversation conversation, PreferenceSnapshot preference,
                                        RealtimeResult realtime) {
        StringBuilder prompt = new StringBuilder("""
                你是一个严谨、友好的中文旅游助手。回答要具体、可执行，并优先依据下面检索到的本地知识。
                规则：
                1. 不要编造门票、评分、地点关系；知识库没有的信息要明确说明可能需要核实。
                2. 推荐知识库地点时，在名称后标注来源序号，例如“故宫博物院[1]”。
                3. 结合知识图谱中的城市、主题和附近美食关系解释推荐理由。
                4. 如果没有检索来源，可以根据通用知识回答，但要避免声称来自本地知识库。
                5. 任何依赖当前时刻的事实都必须有实时工具结果；没有时明确说明无法核实，不得猜测。
                """);
        prompt.insert(0, "【查询分析】\n主要意图：" + intent.primaryIntent()
                + "\n粗粒度路线：" + route.category()
                + "\n次要意图：" + intent.secondaryIntents()
                + "\n已提取约束：" + intent.slots()
                + "\n用户长期偏好：" + preference
                + "\n最近对话：\n" + conversationContextService.formatForPrompt(conversation.context())
                + "\n处理路线：" + route.description() + "\n\n");
        if (realtime != null && realtime.available()) {
            prompt.append("【实时工具结果】\n").append(realtime.content())
                    .append("\n数据来源：").append(realtime.source())
                    .append("；查询时间：").append(realtime.observedAt()).append("\n")
                    .append("回答实时事实时只能使用这段工具结果，不得自行补充其他实时数值。\n");
        } else if (route.realtimeRequired()) {
            prompt.append("注意：当前没有获取到可用的天气或空气质量数据，不得自行猜测实时数值；"
                    + "如回答依赖这些信息，必须明确说明无法核实，并只给条件式建议。\n");
        }
        prompt.append("\n【混合检索来源】\n");
        if (sources.isEmpty()) {
            prompt.append("无\n");
        } else {
            for (int i = 0; i < sources.size(); i++) {
                SourceItemVO source = sources.get(i);
                prompt.append('[').append(i + 1).append("] ")
                        .append(source.title()).append("｜城市:").append(source.city())
                        .append("｜类型:").append(source.type())
                        .append("｜标签:").append(source.tags())
                        .append("｜门票:").append(source.payload().getOrDefault("ticket", "未知"))
                        .append("｜评分:").append(source.payload().getOrDefault("rating", "未知"))
                        .append("｜来源:").append(source.payload().getOrDefault("sourceUri", "本地知识库"))
                        .append("｜内容:").append(source.snippet()).append('\n');
            }
        }
        prompt.append("\n【知识图谱关系】\n");
        if (graph.isEmpty()) {
            prompt.append("无\n");
        } else {
            Map<String, String> labels = new LinkedHashMap<>();
            for (Map<String, Object> node : graphNodes(graph)) {
                labels.put(String.valueOf(node.get("id")), String.valueOf(node.get("label")));
            }
            for (Map<String, Object> edge : graphEdges(graph)) {
                String source = labels.getOrDefault(String.valueOf(edge.get("source")), String.valueOf(edge.get("source")));
                String target = labels.getOrDefault(String.valueOf(edge.get("target")), String.valueOf(edge.get("target")));
                prompt.append(source).append(" --").append(edge.get("label")).append("--> ")
                        .append(target).append('\n');
            }
        }
        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> graphNodes(Map<String, Object> graph) {
        Object nodes = graph.get("nodes");
        return nodes instanceof List<?> ? (List<Map<String, Object>>) nodes : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> graphEdges(Map<String, Object> graph) {
        Object edges = graph.get("edges");
        return edges instanceof List<?> ? (List<Map<String, Object>>) edges : List.of();
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "对话生成失败" : current.getMessage();
    }
}
