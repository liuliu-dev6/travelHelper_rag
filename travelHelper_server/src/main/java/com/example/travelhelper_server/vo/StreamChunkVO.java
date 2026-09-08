package com.example.travelhelper_server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StreamChunkVO {
    private String type="chunk";
    private String content;

    public static StreamChunkVO of(String content) {
        return new StreamChunkVO("chunk",content);
    }
}
