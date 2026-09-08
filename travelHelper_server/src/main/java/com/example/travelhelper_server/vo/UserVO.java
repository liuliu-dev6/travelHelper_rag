package com.example.travelhelper_server.vo;

import com.example.travelhelper_server.entity.User;

import java.time.LocalDateTime;

public record UserVO(Long id, String username, String email, String role, LocalDateTime createdAt) {
    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
