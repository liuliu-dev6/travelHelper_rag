package com.example.travelhelper_server.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationDetailVO(
        String id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ConversationMessageVO> messages
) {
}
