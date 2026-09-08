package com.example.travelhelper_server.vo;

import java.util.List;
import java.util.Map;

public record GraphVO(
        String type,
        String center,
        List<Map<String, Object>> nodes,
        List<Map<String, Object>> edges) {

    @SuppressWarnings("unchecked")
    public static GraphVO from(Map<String, Object> graph) {
        return new GraphVO(
                "graph",
                String.valueOf(graph.getOrDefault("center", "")),
                (List<Map<String, Object>>) graph.getOrDefault("nodes", List.of()),
                (List<Map<String, Object>>) graph.getOrDefault("edges", List.of()));
    }
}
