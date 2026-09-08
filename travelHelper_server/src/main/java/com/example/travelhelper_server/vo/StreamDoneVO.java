package com.example.travelhelper_server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class StreamDoneVO {
    private String type="done";
    private Boolean done=true;
    private Map<String, Object> stats;

    public static StreamDoneVO of(long durationMs, int sourcesCount) {
        return new StreamDoneVO("done", true, Map.of(
                "durationMs", durationMs,
                "sourcesCount", sourcesCount));
    }
}
