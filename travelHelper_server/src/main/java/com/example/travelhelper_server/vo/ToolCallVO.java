package com.example.travelhelper_server.vo;

import java.util.Map;

public record ToolCallVO(
        String type,
        String id,
        String name,
        String status,
        Map<String, Object> args,
        String summary,
        Long durationMs) {

    public static ToolCallVO start(String id, String name, Map<String, Object> args) {
        return new ToolCallVO("tool_call", id, name, "start", args, null, null);
    }

    public static ToolCallVO end(String id, String name, String summary, long durationMs) {
        return new ToolCallVO("tool_call", id, name, "end", null, summary, durationMs);
    }

    public static ToolCallVO error(String id, String name, String summary, long durationMs) {
        return new ToolCallVO("tool_call", id, name, "error", null, summary, durationMs);
    }
}
