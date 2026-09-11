package com.example.travelhelper_server.vo;

import com.example.travelhelper_server.entity.KnowledgeDocument;

import java.time.LocalDateTime;

public record KnowledgeDocumentVO(
        String id, String title, String sourceType, String sourceUri, String sourceId, String mimeType,
        String city, String knowledgeType, String status, int characterCount, int chunkCount,
        String errorMessage, String reviewedBy, LocalDateTime reviewedAt,
        LocalDateTime createdAt, LocalDateTime indexedAt
) {
    public static KnowledgeDocumentVO from(KnowledgeDocument value) {
        return new KnowledgeDocumentVO(value.getId(), value.getTitle(), value.getSourceType().name(),
                value.getSourceUri(), value.getSourceId(), value.getMimeType(), value.getCity(), value.getKnowledgeType(),
                value.getStatus().name(), value.getCharacterCount(), value.getChunkCount(),
                value.getErrorMessage(), value.getReviewedBy(), value.getReviewedAt(),
                value.getCreatedAt(), value.getIndexedAt());
    }
}
