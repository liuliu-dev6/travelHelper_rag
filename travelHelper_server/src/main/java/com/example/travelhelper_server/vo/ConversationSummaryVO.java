package com.example.travelhelper_server.vo;

import java.time.LocalDateTime;

public record ConversationSummaryVO(
        String id,
        String title,
        long messageCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
