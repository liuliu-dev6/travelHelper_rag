package com.example.travelhelper_server.repository;

import com.example.travelhelper_server.entity.KgRelationCandidate;
import com.example.travelhelper_server.extraction.CandidateReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KgRelationCandidateRepository extends JpaRepository<KgRelationCandidate, String> {
    List<KgRelationCandidate> findTop200ByStatusInOrderByCreatedAtDesc(Collection<CandidateReviewStatus> statuses);
    void deleteAllByDocumentIdAndStatusNot(String documentId, CandidateReviewStatus status);
}
