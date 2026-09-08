package com.example.travelhelper_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 父块只负责给生成模型提供完整上下文，不写入Qdrant、不参与粗召回。
 * 小块命中后通过parentChunkId回查这里，避免为同一段正文建立两套向量。
 */
@Entity
@Table(name = "knowledge_parent_chunks", indexes = {
        @Index(name = "idx_parent_chunk_document", columnList = "document_id,parent_index")
})
@Getter @Setter @NoArgsConstructor
public class KnowledgeParentChunk {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Column(name = "parent_index", nullable = false)
    private int parentIndex;

    @Column(name = "section_path", length = 1000)
    private String sectionPath;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "character_count", nullable = false)
    private int characterCount;
}
