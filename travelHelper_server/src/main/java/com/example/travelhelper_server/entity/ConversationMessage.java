package com.example.travelhelper_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_messages",
        uniqueConstraints = @UniqueConstraint(name = "uk_message_conversation_seq",
                columnNames = {"conversation_id", "sequence_no"}),
        indexes = @Index(name = "idx_message_conversation_seq",
                columnList = "conversation_id,sequence_no"))
@Getter
@Setter
@NoArgsConstructor
public class ConversationMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Column(nullable = false, length = 16)
    private String role;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(name = "detail_json", columnDefinition = "LONGTEXT")
    private String detailJson;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void createDate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
