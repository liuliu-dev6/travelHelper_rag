package com.example.travelhelper_server.entity;

import com.example.travelhelper_server.extraction.CandidateReviewStatus;
import com.example.travelhelper_server.extraction.GraphRelationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "kg_relation_candidates", indexes = {
        @Index(name = "idx_kg_relation_document", columnList = "document_id,created_at"),
        @Index(name = "idx_kg_relation_review", columnList = "status,created_at")
})
@Getter @Setter @NoArgsConstructor
public class KgRelationCandidate {
    @Id @Column(length = 36)
    private String id;
    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;
    @Column(name = "source_candidate_id", nullable = false, length = 36)
    private String sourceCandidateId;
    @Column(name = "target_candidate_id", nullable = false, length = 36)
    private String targetCandidateId;
    @Enumerated(EnumType.STRING) @Column(name = "relation_type", nullable = false, length = 32)
    private GraphRelationType relationType;
    @Column(nullable = false)
    private double confidence;
    @Column(nullable = false, length = 1200)
    private String evidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private CandidateReviewStatus status;
    @Column(length = 1200)
    private String issues;
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
