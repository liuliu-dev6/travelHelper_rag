package com.example.travelhelper_server.repository;

import com.example.travelhelper_server.entity.KgEntityCandidate;
import com.example.travelhelper_server.extraction.CandidateReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KgEntityCandidateRepository extends JpaRepository<KgEntityCandidate, String> {
    List<KgEntityCandidate> findAllByDocumentIdOrderByCreatedAtAsc(String documentId);
    boolean existsByDocumentId(String documentId);
    void deleteAllByDocumentIdAndStatusNot(String documentId, CandidateReviewStatus status);
}
