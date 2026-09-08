package com.example.travelhelper_server.entity;

import com.example.travelhelper_server.ingestion.KnowledgeDocumentStatus;
import com.example.travelhelper_server.ingestion.KnowledgeSourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_documents", indexes = {
        @Index(name = "idx_knowledge_document_checksum", columnList = "checksum"),
        @Index(name = "idx_knowledge_document_status", columnList = "status,created_at"),
        @Index(name = "idx_knowledge_document_source", columnList = "source_id,created_at")
})
@Getter @Setter @NoArgsConstructor
public class KnowledgeDocument {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private KnowledgeSourceType sourceType;

    @Column(name = "source_uri", nullable = false, length = 1500)
    private String sourceUri;

    @Column(name = "source_id", length = 36)
    private String sourceId;

    @Column(name = "stored_path", length = 1000)
    private String storedPath;

    @Column(name = "mime_type", length = 160)
    private String mimeType;

    @Column(length = 64, nullable = false)
    private String checksum;

    @Column(length = 120)
    private String city;

    @Column(name = "knowledge_type", length = 32)
    private String knowledgeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeDocumentStatus status;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @PrePersist
    void createDate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
