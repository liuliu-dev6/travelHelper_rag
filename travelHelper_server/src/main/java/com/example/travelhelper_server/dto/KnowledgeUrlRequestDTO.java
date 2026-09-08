package com.example.travelhelper_server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeUrlRequestDTO(
        @NotBlank(message = "请输入网页URL") @Size(max = 1500) String url,
        @Size(max = 300) String title,
        @Size(max = 120) String city,
        @Size(max = 32) String knowledgeType
) {}
