package com.example.travelhelper_server.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 32, message = "用户名长度应为2-32个字符")
        @Pattern(regexp = "^[\\p{L}\\p{N}_-]+$", message = "用户名只能包含文字、数字、下划线或短横线")
        String username,
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱过长")
        String email,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度应为8-72个字符")
        String password
) {}
