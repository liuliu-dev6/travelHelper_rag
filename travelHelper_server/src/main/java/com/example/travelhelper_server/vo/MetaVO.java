package com.example.travelhelper_server.vo;

public record MetaVO(String type, String sessionId, String conversationId) {
    public static MetaVO of(String sessionId, String conversationId) {
        return new MetaVO("meta", sessionId, conversationId);
    }
}
