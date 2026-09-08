package com.example.travelhelper_server.vo;

import com.example.travelhelper_server.entity.ConversationMessage;

import java.time.LocalDateTime;

public record ConversationMessageVO(
        Long id,
        String role,
        String content,
        String detailJson,
        long sequenceNo,
        LocalDateTime createdAt
) {
    public static ConversationMessageVO from(ConversationMessage message) {
        return new ConversationMessageVO(message.getId(), message.getRole(), message.getContent(),
                message.getDetailJson(), message.getSequenceNo(), message.getCreatedAt());
    }
}
