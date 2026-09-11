package com.example.travelhelper_server.dto;

import jakarta.validation.constraints.Size;

public record ReviewDecisionDTO(@Size(max = 500) String reason) {}
