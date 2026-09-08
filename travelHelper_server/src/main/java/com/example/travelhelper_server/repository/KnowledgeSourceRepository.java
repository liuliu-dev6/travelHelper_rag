package com.example.travelhelper_server.repository;

import com.example.travelhelper_server.entity.KnowledgeSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, String> {
    Optional<KnowledgeSource> findByCode(String code);
    List<KnowledgeSource> findAllByOrderByPriorityDescNameAsc();
    List<KnowledgeSource> findTop2ByEnabledTrueAndNextRefreshAtLessThanEqualOrderByPriorityDescNextRefreshAtAsc(
            LocalDateTime now);
}
