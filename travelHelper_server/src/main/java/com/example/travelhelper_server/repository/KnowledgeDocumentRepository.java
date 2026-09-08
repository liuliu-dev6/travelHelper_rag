package com.example.travelhelper_server.repository;

import com.example.travelhelper_server.entity.KnowledgeDocument;
import com.example.travelhelper_server.ingestion.KnowledgeDocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {
    List<KnowledgeDocument> findTop100ByOrderByCreatedAtDesc();
    Optional<KnowledgeDocument> findFirstByChecksumAndStatusOrderByCreatedAtDesc(
            String checksum, KnowledgeDocumentStatus status);
}
