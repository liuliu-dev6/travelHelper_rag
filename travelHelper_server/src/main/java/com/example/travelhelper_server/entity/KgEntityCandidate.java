package com.example.travelhelper_server.entity;

import com.example.travelhelper_server.extraction.CandidateReviewStatus;
import com.example.travelhelper_server.extraction.GraphEntityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "kg_entity_candidates", indexes = {
        @Index(name = "idx_kg_entity_document", columnList = "document_id,created_at"),
        @Index(name = "idx_kg_entity_review", columnList = "status,created_at")
})
@Getter @Setter @NoArgsConstructor
public class KgEntityCandidate {
    @Id @Column(length = 36)
    private String id;
    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;
    @Column(name = "temporary_id", nullable = false, length = 80)
    private String temporaryId;
    @Enumerated(EnumType.STRING) @Column(name = "entity_type", nullable = false, length = 24)
    private GraphEntityType entityType;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "canonical_name", nullable = false, length = 200)
    private String canonicalName;
    @Column(name = "canonical_key", nullable = false, length = 500)
    private String canonicalKey;
    @Column(length = 120)
    private String city;
    @Column(name = "aliases_json", length = 2000)
    private String aliasesJson;
    @Column(nullable = false)
    private double confidence;
    @Column(length = 1200)
    private String evidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private CandidateReviewStatus status;
    @Column(length = 1200)
    private String issues;
    @Column(name = "aligned_entity_id", length = 160)
    private String alignedEntityId;
    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void createDate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
