package com.example.travelhelper_server.repository;

import com.example.travelhelper_server.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, String> {
    List<KnowledgeChunk> findAllByDocumentIdOrderByChunkIndex(String documentId);
    void deleteAllByDocumentId(String documentId);
}
