package com.example.travelhelper_server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @NotBlank(message = "账号不能为空") String account,
        @NotBlank(message = "密码不能为空")
        @Size(max = 72, message = "密码过长") String password
) {}
