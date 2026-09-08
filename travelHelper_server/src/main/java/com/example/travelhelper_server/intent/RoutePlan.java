package com.example.travelhelper_server.intent;

public record RoutePlan(
        RouteCategory category,
        boolean vectorSearch,
        boolean graphSearch,
        String typeFilter,
        boolean realtimeRequired,
        boolean memoryUnavailable,
        String clarification,
        String description
) {
}
