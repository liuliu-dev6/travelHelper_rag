package com.example.travelhelper_server.dto;

import com.example.travelhelper_server.subscription.KnowledgeRefreshPolicy;
import jakarta.validation.constraints.NotNull;

public record KnowledgeSourceUpdateDTO(
        @NotNull Boolean enabled,
        @NotNull KnowledgeRefreshPolicy refreshPolicy
) {}
