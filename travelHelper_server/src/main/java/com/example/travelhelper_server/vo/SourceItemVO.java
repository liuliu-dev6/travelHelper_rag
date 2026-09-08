package com.example.travelhelper_server.vo;

import java.util.List;
import java.util.Map;

public record SourceItemVO(
        String id,
        String type,
        String title,
        String city,
        List<String> tags,
        Double score,
        String snippet,
        Map<String, Object> payload) {
}
