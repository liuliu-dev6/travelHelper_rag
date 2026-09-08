package com.example.travelhelper_server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "knowledge_chunks", indexes = {
        @Index(name = "idx_knowledge_chunk_document", columnList = "document_id,chunk_index")
})
@Getter @Setter @NoArgsConstructor
public class KnowledgeChunk {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "parent_chunk_id", length = 36)
    private String parentChunkId;

    @Column(name = "section_path", length = 1000)
    private String sectionPath;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false)
    private int characterCount;

    @Column(name = "qdrant_point_id", nullable = false, unique = true, length = 36)
    private String qdrantPointId;
}
