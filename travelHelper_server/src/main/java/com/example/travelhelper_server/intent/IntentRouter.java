package com.example.travelhelper_server.intent;

import org.springframework.stereotype.Service;

@Service
public class IntentRouter {

    public RoutePlan route(QueryIntent intent) {
        QuerySlots slots = intent.slots();
        if (intent.primaryIntent() == IntentType.ITINERARY_PLANNING && slots.cities().isEmpty()) {
            return new RoutePlan(RouteCategory.PLANNING, false, false, null, false, false,
                    "请先告诉我想去哪个城市，我才能为你规划具体行程。",
                    "行程规划缺少目的地，先追问城市");
        }

        return switch (intent.primaryIntent()) {
            case ATTRACTION_RECOMMENDATION ->
                    new RoutePlan(RouteCategory.RECOMMENDATION, true, true, "poi", false, false, null, "景点语义检索 + 图谱扩展");
            case FOOD_RECOMMENDATION ->
                    new RoutePlan(RouteCategory.RECOMMENDATION, true, true, "food", false, false, null,
                            "美食精确/语义检索 + POI图谱关系");
            case ITINERARY_PLANNING ->
                    new RoutePlan(RouteCategory.PLANNING, true, true, "poi", hasRealtime(intent), false, null, "行程候选检索 + 图谱扩展");
            case ENTITY_FACT_QA, RELATION_QUERY ->
                    new RoutePlan(RouteCategory.FACT_RELATION, true, true, null, hasRealtime(intent), false, null, "实体检索 + 图谱关系查询");
            case REALTIME_INFO ->
                    new RoutePlan(RouteCategory.REALTIME, false, false, null, true, false, null, "实时工具调用与安全降级");
            case PREFERENCE_MEMORY ->
                    new RoutePlan(RouteCategory.GENERAL_CHAT, false, false, null, false, false, null, "用户偏好记忆");
            case GENERAL_CHAT -> {
                // SAFE_DEFAULT 会把 needs 标成需要检索；明确寒暄则直接走普通对话。
                boolean retrieve = intent.needs().vectorSearch();
                yield new RoutePlan(RouteCategory.GENERAL_CHAT, retrieve, retrieve && intent.needs().graphSearch(), null,
                        hasRealtime(intent), false, null,
                        retrieve ? "未识别问句，使用默认 RAG" : "普通对话");
            }
        };
    }

    private boolean hasRealtime(QueryIntent intent) {
        return intent.needs().realtimeApi()
                || intent.secondaryIntents().contains(IntentType.REALTIME_INFO);
    }
}
