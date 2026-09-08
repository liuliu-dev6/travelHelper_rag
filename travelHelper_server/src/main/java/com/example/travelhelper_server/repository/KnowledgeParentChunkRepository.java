package com.example.travelhelper_server.repository;

import com.example.travelhelper_server.entity.KnowledgeParentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeParentChunkRepository extends JpaRepository<KnowledgeParentChunk, String> {
    void deleteAllByDocumentId(String documentId);
}
