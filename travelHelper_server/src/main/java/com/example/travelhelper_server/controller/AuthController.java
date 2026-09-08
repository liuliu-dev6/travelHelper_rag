package com.example.travelhelper_server.controller;

import com.example.travelhelper_server.dto.ChangePasswordRequestDTO;
import com.example.travelhelper_server.dto.LoginRequestDTO;
import com.example.travelhelper_server.dto.RegisterRequestDTO;
import com.example.travelhelper_server.service.AuthService;
import com.example.travelhelper_server.vo.Result;
import com.example.travelhelper_server.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/csrf")
    public Result<Map<String, String>> csrf(CsrfToken token) {
        return Result.ok(Map.of("token", token.getToken()));
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    public Result<UserVO> login(@Valid @RequestBody LoginRequestDTO login,
                                HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.account().trim(), login.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            return Result.ok(UserVO.from(authService.requireUser(authentication.getName())));
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(Authentication authentication,
                               HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserVO> me(Principal principal) {
        return Result.ok(UserVO.from(authService.requireUser(principal.getName())));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(Principal principal,
                                       @Valid @RequestBody ChangePasswordRequestDTO request) {
        authService.changePassword(principal.getName(), request);
        return Result.ok();
    }
}
