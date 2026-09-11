package com.example.travelhelper_server.vo;

import com.example.travelhelper_server.entity.KgEntityCandidate;
import com.example.travelhelper_server.entity.KgRelationCandidate;

import java.time.LocalDateTime;

public record GraphCandidateVO(
        String id, String documentId, String relationType, double confidence, String evidence,
        String status, String issues, EntityVO source, EntityVO target, LocalDateTime createdAt
) {
    public static GraphCandidateVO from(KgRelationCandidate relation,
                                        KgEntityCandidate source, KgEntityCandidate target) {
        return new GraphCandidateVO(relation.getId(), relation.getDocumentId(), relation.getRelationType().name(),
                relation.getConfidence(), relation.getEvidence(), relation.getStatus().name(), relation.getIssues(),
                EntityVO.from(source), EntityVO.from(target), relation.getCreatedAt());
    }

    public record EntityVO(String id, String type, String name, String city, double confidence,
                           String status, String issues, String alignedEntityId) {
        static EntityVO from(KgEntityCandidate value) {
            return new EntityVO(value.getId(), value.getEntityType().name(), value.getName(), value.getCity(),
                    value.getConfidence(), value.getStatus().name(), value.getIssues(), value.getAlignedEntityId());
        }
    }
}
