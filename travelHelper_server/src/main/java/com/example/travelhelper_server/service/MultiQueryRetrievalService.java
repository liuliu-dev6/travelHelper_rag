package com.example.travelhelper_server.service;

import com.example.travelhelper_server.intent.QueryIntent;
import com.example.travelhelper_server.intent.IntentType;
import com.example.travelhelper_server.intent.RouteCategory;
import com.example.travelhelper_server.intent.RoutePlan;
import com.example.travelhelper_server.service.UserPreferenceService.PreferenceSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MultiQueryRetrievalService {
    private static final int RRF_K = 60;
    private final QueryRewriteService queryRewriteService;
    private final VectorSearchService vectorSearchService;
    private final UserPreferenceService preferenceService;
    private final ExactEntitySearchService exactEntitySearchService;
    private final Bm25SearchService bm25SearchService;
    private final KnowledgeSourceMatcher sourceMatcher;
    private final double vectorWeight;
    private final double bm25Weight;
    private final double rewriteWeight;

    @Value("${rag.fusion.abstract-vector-weight:1.0}")
    private double abstractVectorWeight = 1.0;
    @Value("${rag.fusion.abstract-bm25-weight:0.0}")
    private double abstractBm25Weight = 0.0;
    @Value("${rag.fusion.abstract-effective-terms:true}")
    private boolean abstractEffectiveTerms = true;

    public MultiQueryRetrievalService(QueryRewriteService queryRewriteService,
                                      VectorSearchService vectorSearchService,
                                      UserPreferenceService preferenceService,
                                      ExactEntitySearchService exactEntitySearchService,
                                      Bm25SearchService bm25SearchService,
                                      KnowledgeSourceMatcher sourceMatcher,
                                      @Value("${rag.fusion.vector-weight:1.0}") double vectorWeight,
                                      @Value("${rag.fusion.bm25-weight:0.05}") double bm25Weight,
                                      @Value("${rag.fusion.rewrite-weight:0.01}") double rewriteWeight) {
        if (vectorWeight < 0 || bm25Weight < 0 || vectorWeight + bm25Weight == 0
                || rewriteWeight < 0 || rewriteWeight > 1) {
            throw new IllegalArgumentException("RRF通道权重必须非负、至少一个通道大于0，且改写权重不超过1");
        }
        this.queryRewriteService = queryRewriteService;
        this.vectorSearchService = vectorSearchService;
        this.preferenceService = preferenceService;
        this.exactEntitySearchService = exactEntitySearchService;
        this.bm25SearchService = bm25SearchService;
        this.sourceMatcher = sourceMatcher;
        this.vectorWeight = vectorWeight;
        this.bm25Weight = bm25Weight;
        this.rewriteWeight = rewriteWeight;
    }

    public RetrievalResult search(String query, QueryIntent intent, RoutePlan route, String city,
                                  PreferenceSnapshot preference, int topK) throws Exception {
        return search(query, query, intent, route, city, preference, topK);
    }

    public RetrievalResult search(String query, String exactQuery, QueryIntent intent, RoutePlan route, String city,
                                  PreferenceSnapshot preference, int topK) throws Exception {
        List<Map<String, Object>> exactMatches = safeExactSearch(exactQuery, city, null, 5);
        KnowledgeSourceMatcher.SourceConstraint sourceConstraint = safeSourceMatch(exactQuery, city);
        String sourceId = sourceConstraint == null ? null : sourceConstraint.sourceId();
        List<Map<String, Object>> exactCandidates = route.typeFilter() == null ? exactMatches
                : exactMatches.stream().filter(hit -> route.typeFilter().equalsIgnoreCase(
                        String.valueOf(hit.get("type")))).toList();

        // 事实/关系问题已经精确锁定实体时，直接交给Neo4j验证关系，不再重复调用Embedding和BM25。
        if (route.category() == RouteCategory.FACT_RELATION && !exactMatches.isEmpty()) {
            return new RetrievalResult(List.of(), List.of(),
                    exactCandidates.stream().limit(topK).toList(), exactMatches, "EXACT_GRAPH");
        }
        // “某景点有什么美食”先锁定POI，再查询HAS_FOOD；向量召回不能替代确定性关系。
        if (intent.primaryIntent() == IntentType.FOOD_RECOMMENDATION
                && exactMatches.stream().anyMatch(this::isPoi)) {
            return new RetrievalResult(List.of(), List.of(),
                    exactCandidates.stream().limit(topK).toList(), exactMatches, "EXACT_GRAPH");
        }

        ChannelWeights channelWeights = channelWeights(query, route, intent, sourceConstraint);
        boolean effectiveTerms = abstractEffectiveTerms && "SEMANTIC_RECOMMENDATION".equals(channelWeights.reason());
        QueryPlan queryPlan = planQueries(query, route, intent, preference, sourceConstraint);
        List<String> vectorQueries = channelWeights.vector() > 0 ? queryPlan.queries() : List.of();
        int perQueryLimit = Math.max(10, topK * 2);
        List<List<Map<String, Object>>> vectorRankings = runSearches(vectorQueries,
                item -> vectorSearchService.search(item, perQueryLimit, city, route.typeFilter(), sourceId));
        List<Map<String, Object>> vectorChannel = fuseChannel(
                vectorRankings, queryPlan.weights(), perQueryLimit, "VECTOR");
        // 进入RAG路线后构建关键词与语义双路候选池；明确实体关系已在上方精确命中短路。
        boolean abstractFallback = "SEMANTIC_RECOMMENDATION".equals(channelWeights.reason()) && vectorChannel.isEmpty();
        boolean useBm25 = channelWeights.bm25() > 0 || abstractFallback;
        List<String> keywordQueries = useBm25 ? queryPlan.queries() : List.of();
        List<List<Map<String, Object>>> bm25Rankings = useBm25
                ? runSearches(keywordQueries, item -> effectiveTerms
                ? bm25SearchService.search(item, perQueryLimit, city, route.typeFilter(), sourceId, true)
                : bm25SearchService.search(item, perQueryLimit, city, route.typeFilter(), sourceId))
                : List.of();

        // 需要多通道时先在通道内部融合，再进行跨通道融合，避免查询数量形成隐式权重。
        List<Map<String, Object>> bm25Channel = fuseChannel(
                bm25Rankings, queryPlan.weights(), perQueryLimit, "BM25");
        if (vectorChannel.isEmpty() && bm25Channel.isEmpty() && exactMatches.isEmpty()) {
            throw new IllegalStateException("精确召回、BM25和向量检索均无可用结果");
        }
        List<Map<String, Object>> fused = fuseChannels(
                vectorChannel, bm25Channel, exactCandidates, city, route.typeFilter(), preference, topK,
                abstractFallback ? new ChannelWeights(0, 1, "ABSTRACT_BM25_FALLBACK") : channelWeights);
        String strategy = vectorChannel.isEmpty() && !bm25Channel.isEmpty()
                ? "BM25_ONLY"
                : bm25Channel.isEmpty()
                ? (vectorQueries.size() > 1 ? "VECTOR_RRF" : "VECTOR_ONLY")
                : "DYNAMIC_WEIGHTED_RRF";
        if (sourceConstraint != null) strategy = "ENTITY_SCOPED_" + strategy;
        return new RetrievalResult(vectorQueries, keywordQueries, fused, exactMatches, strategy);
    }

    private boolean isPoi(Map<String, Object> hit) {
        return "poi".equalsIgnoreCase(String.valueOf(hit.get("type")));
    }

    private QueryPlan planQueries(String query, RoutePlan route, QueryIntent intent,
                                  PreferenceSnapshot preference,
                                  KnowledgeSourceMatcher.SourceConstraint constraint) throws Exception {
        if (constraint != null && route.category() == RouteCategory.FACT_RELATION) {
            List<String> queries = new java.util.ArrayList<>();
            List<Double> weights = new java.util.ArrayList<>();
            queries.add(query); weights.add(1.0);
            addFacet(query, constraint.sourceName(), queries, weights,
                    "动物|看什么|有什么可以看", "动物 展示 物种", .70);
            addFacet(query, constraint.sourceName(), queries, weights,
                    "门票|票价|多少钱|收费", "门票 票价 收费", .80);
            addFacet(query, constraint.sourceName(), queries, weights,
                    "开放|营业|几点|闭园", "开放时间 营业时间 闭园", .80);
            addFacet(query, constraint.sourceName(), queries, weights,
                    "预约|限流", "预约 规则 限流", .75);
            addFacet(query, constraint.sourceName(), queries, weights,
                    "观光车|索道|地铁|交通", "观光车 索道 交通 运营", .75);
            addFacet(query, constraint.sourceName(), queries, weights,
                    "展览|陈列|展出", "展览 陈列 展出", .75);
            return new QueryPlan(queries.stream().limit(4).toList(), weights.stream().limit(4).toList());
        }
        List<String> queries = shouldRewrite(route, intent)
                ? queryRewriteService.rewrite(query, intent, preference) : List.of(query);
        List<Double> weights = new java.util.ArrayList<>();
        for (int index = 0; index < queries.size(); index++) weights.add(index == 0 ? 1.0 : rewriteWeight);
        return new QueryPlan(queries, weights);
    }

    private void addFacet(String query, String entity, List<String> queries, List<Double> weights,
                          String pattern, String evidenceTerms, double weight) {
        if (!java.util.regex.Pattern.compile(pattern).matcher(query).find()) return;
        String planned = entity + " " + evidenceTerms;
        if (!queries.contains(planned)) {
            queries.add(planned);
            weights.add(weight);
        }
    }

    private List<Map<String, Object>> safeExactSearch(String query, String city, String type, int limit) {
        try {
            return exactEntitySearchService.search(query, city, type, limit);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private KnowledgeSourceMatcher.SourceConstraint safeSourceMatch(String query, String city) {
        if (sourceMatcher == null) return null;
        try {
            return sourceMatcher.match(query, city).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean shouldRewrite(RoutePlan route, QueryIntent intent) {
        // 简单推荐原问题已经包含城市/主题/同行人时，LLM改写通常只增加延迟和泛化噪声。
        // 仅规划或明确复杂问题才使用多查询改写。
        return route.category() == RouteCategory.PLANNING
                || intent.complexity() == com.example.travelhelper_server.intent.QueryComplexity.COMPLEX;
    }

    private ChannelWeights channelWeights(String query, RoutePlan route, QueryIntent intent,
                                           KnowledgeSourceMatcher.SourceConstraint sourceConstraint) {
        if (sourceConstraint != null) {
            // 已锁定官网来源后，事实关键词比跨主题语义更可靠；向量只补同源近义表达。
            return new ChannelWeights(.25, 1.0, "SOURCE_FACT");
        }
        if (route.category() == RouteCategory.FACT_RELATION) {
            return new ChannelWeights(.30, 1.0, "FACT_RELATION");
        }
        if (route.category() == RouteCategory.RECOMMENDATION) {
            // “亲子/免费/夜景”等是库内显式标签，词法通道应主导；抽象需求仍保留语义主导。
            return hasStrongLexicalConstraint(query)
                    ? new ChannelWeights(0, 1.0, "TAG_CONSTRAINED_RECOMMENDATION")
                    : new ChannelWeights(abstractVectorWeight, abstractBm25Weight, "SEMANTIC_RECOMMENDATION");
        }
        if (route.category() == RouteCategory.PLANNING) {
            return new ChannelWeights(.70, .85, "PLANNING");
        }
        return new ChannelWeights(vectorWeight, bm25Weight, "DEFAULT");
    }

    public boolean hasStrongLexicalConstraint(String query) {
        if (query == null || query.isBlank()) return false;
        return java.util.regex.Pattern.compile(
                "亲子|孩子|儿童|情侣|约会|免费|夜景|博物馆|历史|文化|自然|科技|拍照|徒步|美食|小吃|门票|开放|预约")
                .matcher(query).find();
    }

    private List<List<Map<String, Object>>> runSearches(List<String> queries, SearchOperation operation) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<List<Map<String, Object>>>> futures = queries.stream()
                    .map(query -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return operation.search(query);
                        } catch (Exception ignored) {
                            return List.<Map<String, Object>>of();
                        }
                    }, executor)).toList();
            // 保留查询位置：第一个排名始终对应原问题，后续排名才是低权重改写。
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    /** 保留为包级方法，供单元测试直接验证RRF排名语义。 */
    List<Map<String, Object>> fuse(List<List<Map<String, Object>>> rankings,
                                   PreferenceSnapshot preference, int topK) {
        return fuseRankings(rankings, preference, topK, true, "RRF");
    }

    List<Map<String, Object>> fuseChannel(List<List<Map<String, Object>>> rankings,
                                          int limit, String channel) {
        List<Double> weights = new java.util.ArrayList<>();
        for (int index = 0; index < rankings.size(); index++) weights.add(index == 0 ? 1.0 : rewriteWeight);
        return fuseChannel(rankings, weights, limit, channel);
    }

    private List<Map<String, Object>> fuseChannel(List<List<Map<String, Object>>> rankings,
                                                   List<Double> queryWeights, int limit, String channel) {
        return fuseRankings(rankings, PreferenceSnapshot.empty(), limit, false, channel, queryWeights).stream()
                .map(hit -> {
                    Map<String, Object> result = new LinkedHashMap<>(hit);
                    String prefix = "VECTOR".equals(channel) ? "_vector" : "_bm25";
                    result.put(prefix + "RrfScore", result.remove("_rrfScore"));
                    result.put(prefix + "MatchedQueries", result.remove("_matchedQueries"));
                    result.put("_retrievalChannel", channel);
                    return result;
                }).toList();
    }

    List<Map<String, Object>> fuseChannels(List<Map<String, Object>> vectorRanking,
                                           List<Map<String, Object>> bm25Ranking,
                                           List<Map<String, Object>> exactMatches,
                                           PreferenceSnapshot preference, int topK) {
        return fuseChannels(vectorRanking, bm25Ranking, exactMatches, null, null, preference, topK);
    }

    private List<Map<String, Object>> fuseChannels(List<Map<String, Object>> vectorRanking,
                                                   List<Map<String, Object>> bm25Ranking,
                                                   List<Map<String, Object>> exactMatches,
                                                   String city, String type,
                                                   PreferenceSnapshot preference, int topK) {
        return fuseChannels(vectorRanking, bm25Ranking, exactMatches, city, type, preference, topK,
                new ChannelWeights(vectorWeight, bm25Weight, "CONFIGURED"));
    }

    private List<Map<String, Object>> fuseChannels(List<Map<String, Object>> vectorRanking,
                                                   List<Map<String, Object>> bm25Ranking,
                                                   List<Map<String, Object>> exactMatches,
                                                   String city, String type,
                                                   PreferenceSnapshot preference, int topK,
                                                   ChannelWeights weights) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (Map<String, Object> exact : exactMatches) {
            String id = entityId(exact);
            if (id.isBlank()) continue;
            Candidate candidate = candidates.computeIfAbsent(id, ignored -> new Candidate(exact));
            candidate.absorb(exact);
            candidate.exactBoost = 1.0;
            candidate.channels.add("EXACT");
        }
        addRanking(candidates, vectorRanking, "VECTOR", weights.vector());
        addRanking(candidates, bm25Ranking, "BM25", weights.bm25());
        List<Candidate> eligible = candidates.values().stream()
                .filter(candidate -> matchesCity(city, candidate.hit.get("city")))
                .filter(candidate -> matchesType(type, candidate.hit)).toList();
        for (Candidate candidate : eligible) {
            candidate.preferenceBoost = preferenceService.rankingBoost(candidate.hit, preference);
        }
        return eligible.stream()
                .sorted(Comparator.comparingDouble(Candidate::finalScore).reversed())
                .limit(topK).map(Candidate::toMap).toList();
    }

    private List<Map<String, Object>> fuseRankings(List<List<Map<String, Object>>> rankings,
                                                   PreferenceSnapshot preference, int topK,
                                                   boolean applyPreference, String channel) {
        return fuseRankings(rankings, preference, topK, applyPreference, channel, List.of());
    }

    private List<Map<String, Object>> fuseRankings(List<List<Map<String, Object>>> rankings,
                                                   PreferenceSnapshot preference, int topK,
                                                   boolean applyPreference, String channel,
                                                   List<Double> queryWeights) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (int queryIndex = 0; queryIndex < rankings.size(); queryIndex++) {
            List<Map<String, Object>> ranking = rankings.get(queryIndex);
            double queryWeight = queryIndex < queryWeights.size() ? queryWeights.get(queryIndex) : 1.0;
            for (int rank = 0; rank < ranking.size(); rank++) {
                Map<String, Object> hit = ranking.get(rank);
                String id = entityId(hit);
                if (id.isBlank()) continue;
                Candidate candidate = candidates.computeIfAbsent(id, ignored -> new Candidate(hit));
                candidate.absorb(hit);
                candidate.rrfScore += queryWeight * rrf(rank);
                candidate.matchedQueries++;
                candidate.channels.add(channel);
            }
        }
        if (applyPreference) {
            for (Candidate candidate : candidates.values()) {
                candidate.preferenceBoost = preferenceService.rankingBoost(candidate.hit, preference);
            }
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::finalScore).reversed())
                .limit(topK).map(Candidate::toMap).toList();
    }

    private void addRanking(Map<String, Candidate> candidates, List<Map<String, Object>> ranking,
                            String channel, double channelWeight) {
        for (int rank = 0; rank < ranking.size(); rank++) {
            Map<String, Object> hit = ranking.get(rank);
            String id = entityId(hit);
            if (id.isBlank()) continue;
            Candidate candidate = candidates.computeIfAbsent(id, ignored -> new Candidate(hit));
            candidate.absorb(hit);
            candidate.rrfScore += channelWeight * rrf(rank);
            candidate.matchedQueries++;
            candidate.channels.add(channel);
        }
    }

    private double rrf(int zeroBasedRank) {
        return 1.0 / (RRF_K + zeroBasedRank + 1);
    }

    private String entityId(Map<String, Object> hit) {
        return String.valueOf(hit.getOrDefault("entityId", hit.getOrDefault("id", "")));
    }

    private boolean matchesCity(String expected, Object actualValue) {
        if (expected == null || expected.isBlank()) return true;
        String left = normalize(expected);
        String right = normalize(actualValue);
        return left.equals(right) || left.contains(right) || right.contains(left);
    }

    private boolean matchesType(String expected, Map<String, Object> hit) {
        if (expected == null || expected.isBlank()) return true;
        String actual = normalize(hit.get("type"));
        if (normalize(expected).equals(actual)) return true;
        if (!"document_chunk".equals(actual)) return false;
        String knowledgeType = normalize(hit.get("knowledgeType"));
        return knowledgeType.isBlank() || "guide".equals(knowledgeType) || "culture".equals(knowledgeType)
                || normalize(expected).equals(knowledgeType)
                || ("poi".equals(normalize(expected)) && Set.of("notice", "opening", "ticket", "exhibition")
                .contains(knowledgeType));
    }

    private String normalize(Object value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKC)
                .replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    @FunctionalInterface
    private interface SearchOperation {
        List<Map<String, Object>> search(String query) throws Exception;
    }

    public record RetrievalResult(List<String> queries, List<String> bm25Queries,
                                  List<Map<String, Object>> hits,
                                  List<Map<String, Object>> exactMatches,
                                  String strategy) {
        public RetrievalResult(List<String> queries, List<String> bm25Queries,
                               List<Map<String, Object>> hits,
                               List<Map<String, Object>> exactMatches) {
            this(queries, bm25Queries, hits, exactMatches, "WEIGHTED_RRF");
        }
    }

    private static class Candidate {
        private final Map<String, Object> hit;
        private final Set<String> channels = new LinkedHashSet<>();
        private double rrfScore;
        private double bestVectorScore;
        private double bestBm25Score;
        private double preferenceBoost;
        private double exactBoost;
        private int matchedQueries;

        private Candidate(Map<String, Object> hit) {
            this.hit = new LinkedHashMap<>(hit);
            absorb(hit);
        }

        private void absorb(Map<String, Object> incoming) {
            incoming.forEach((key, value) -> {
                Object current = hit.get(key);
                if (current == null || current instanceof String text && text.isBlank()
                        || current instanceof List<?> list && list.isEmpty()) hit.put(key, value);
            });
            bestVectorScore = Math.max(bestVectorScore, number(incoming.get("_score")));
            bestBm25Score = Math.max(bestBm25Score, number(incoming.get("_bm25Score")));
            if (Boolean.TRUE.equals(incoming.get("_exactMatch"))) exactBoost = 1.0;
        }

        private double finalScore() {
            return exactBoost + rrfScore + preferenceBoost;
        }

        private Map<String, Object> toMap() {
            hit.put("_score", bestVectorScore);
            hit.put("_bm25Score", bestBm25Score);
            hit.put("_rrfScore", rrfScore);
            hit.put("_preferenceBoost", preferenceBoost);
            hit.put("_exactBoost", exactBoost);
            hit.put("_matchedQueries", matchedQueries);
            hit.put("_retrievalChannels", List.copyOf(channels));
            return hit;
        }
    }

    private record QueryPlan(List<String> queries, List<Double> weights) {}

    private record ChannelWeights(double vector, double bm25, String reason) {
        ChannelWeights {
            if (!Double.isFinite(vector) || !Double.isFinite(bm25)
                    || vector < 0 || bm25 < 0 || vector + bm25 <= 0) {
                throw new IllegalArgumentException("RRF通道权重必须有限、非负且至少一路启用");
            }
        }
    }

}
