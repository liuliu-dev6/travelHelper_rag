package com.example.travelhelper_server.service;

import com.example.travelhelper_server.dto.ChangePasswordRequestDTO;
import com.example.travelhelper_server.dto.RegisterRequestDTO;
import com.example.travelhelper_server.entity.User;
import com.example.travelhelper_server.repository.UserRepository;
import com.example.travelhelper_server.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserVO register(RegisterRequestDTO request) {
        validatePasswordLength(request.password());
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "邮箱已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return UserVO.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效"));
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequestDTO request) {
        validatePasswordLength(request.newPassword());
        User user = requireUser(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码的 UTF-8 长度不能超过72字节");
        }
    }
}
