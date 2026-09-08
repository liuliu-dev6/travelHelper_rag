package com.example.travelhelper_server.vo;

public record KnowledgeRefreshResultVO(
        String outcome,
        boolean changed,
        KnowledgeSourceVO source
) {}
