package com.example.travelhelper_server.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequestDTO(
        @Size(max = 120, message = "会话标题不能超过120字") String title
) {
}
