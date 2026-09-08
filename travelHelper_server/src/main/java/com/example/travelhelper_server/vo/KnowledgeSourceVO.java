package com.example.travelhelper_server.vo;

import com.example.travelhelper_server.entity.KnowledgeSource;

import java.time.LocalDateTime;

public record KnowledgeSourceVO(
        String id, String code, String name, String url, String city, String entityName,
        String knowledgeType, String crawlKeywords, int maxLinkedPages,
        String refreshPolicy, boolean enabled, int priority, String status,
        LocalDateTime lastCheckedAt, LocalDateTime lastSuccessAt, LocalDateTime nextRefreshAt,
        String lastError, String currentDocumentId
) {
    public static KnowledgeSourceVO from(KnowledgeSource source) {
        return new KnowledgeSourceVO(source.getId(), source.getCode(), source.getName(), source.getUrl(),
                source.getCity(), source.getEntityName(), source.getKnowledgeType(),
                source.getCrawlKeywords(), source.getMaxLinkedPages(),
                source.getRefreshPolicy().name(), source.isEnabled(), source.getPriority(),
                source.getStatus().name(), source.getLastCheckedAt(), source.getLastSuccessAt(),
                source.getNextRefreshAt(), source.getLastError(), source.getCurrentDocumentId());
    }
}
