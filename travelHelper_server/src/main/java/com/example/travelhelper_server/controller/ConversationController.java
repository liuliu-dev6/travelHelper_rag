package com.example.travelhelper_server.controller;

import com.example.travelhelper_server.dto.CreateConversationRequestDTO;
import com.example.travelhelper_server.service.ConversationService;
import com.example.travelhelper_server.vo.ConversationDetailVO;
import com.example.travelhelper_server.vo.ConversationSummaryVO;
import com.example.travelhelper_server.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public Result<ConversationSummaryVO> create(Principal principal,
                                                @Valid @RequestBody(required = false)
                                                CreateConversationRequestDTO request) {
        return Result.ok(conversationService.create(principal.getName(), request == null ? null : request.title()));
    }

    @GetMapping
    public Result<List<ConversationSummaryVO>> list(Principal principal) {
        return Result.ok(conversationService.list(principal.getName()));
    }

    @GetMapping("/{id}")
    public Result<ConversationDetailVO> get(Principal principal, @PathVariable String id) {
        return Result.ok(conversationService.get(principal.getName(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(Principal principal, @PathVariable String id) {
        conversationService.delete(principal.getName(), id);
        return Result.ok();
    }
}
