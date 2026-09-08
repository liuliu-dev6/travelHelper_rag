package com.example.travelhelper_server.entity;

import com.example.travelhelper_server.subscription.KnowledgeRefreshPolicy;
import com.example.travelhelper_server.subscription.KnowledgeSourceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_sources", indexes = {
        @Index(name = "uk_knowledge_source_code", columnList = "code", unique = true),
        @Index(name = "idx_knowledge_source_due", columnList = "enabled,next_refresh_at")
})
@Getter @Setter @NoArgsConstructor
public class KnowledgeSource {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 1500)
    private String url;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(name = "entity_name", length = 200)
    private String entityName;

    @Column(name = "knowledge_type", nullable = false, length = 32)
    private String knowledgeType;

    /** 空格分隔的受控链接标题关键词；仅跟进同域名且标题命中的少量详情页。 */
    @Column(name = "crawl_keywords", length = 500)
    private String crawlKeywords;

    @Column(name = "max_linked_pages", nullable = false)
    private int maxLinkedPages = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "refresh_policy", nullable = false, length = 24)
    private KnowledgeRefreshPolicy refreshPolicy;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private int priority = 50;

    @Column(length = 500)
    private String etag;

    @Column(name = "last_modified", length = 160)
    private String lastModified;

    @Column(name = "last_checksum", length = 64)
    private String lastChecksum;

    @Column(name = "current_document_id", length = 36)
    private String currentDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeSourceStatus status = KnowledgeSourceStatus.PENDING;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "next_refresh_at")
    private LocalDateTime nextRefreshAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void createDates() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateDate() {
        updatedAt = LocalDateTime.now();
    }
}
