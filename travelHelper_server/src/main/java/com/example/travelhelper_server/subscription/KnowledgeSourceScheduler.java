package com.example.travelhelper_server.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "knowledge.sources.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeSourceScheduler {
    private final KnowledgeSourceService sourceService;

    @Scheduled(initialDelayString = "${knowledge.sources.initial-delay-ms:30000}",
            fixedDelayString = "${knowledge.sources.scan-interval-ms:60000}")
    public void refreshDue() {
        sourceService.refreshDueSources();
    }
}
