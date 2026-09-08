package com.example.travelhelper_server.subscription;

import java.time.Duration;

public enum KnowledgeRefreshPolicy {
    EVERY_2_HOURS(Duration.ofHours(2)),
    DAILY(Duration.ofDays(1)),
    WEEKLY(Duration.ofDays(7)),
    MONTHLY(Duration.ofDays(30)),
    QUARTERLY(Duration.ofDays(90)),
    MANUAL(null);

    private final Duration interval;

    KnowledgeRefreshPolicy(Duration interval) {
        this.interval = interval;
    }

    public Duration interval() {
        return interval;
    }
}
