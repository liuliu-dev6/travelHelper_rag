package com.example.travelhelper_server.controller;

import com.example.travelhelper_server.dto.KnowledgeSourceUpdateDTO;
import com.example.travelhelper_server.subscription.KnowledgeSourceService;
import com.example.travelhelper_server.vo.KnowledgeRefreshResultVO;
import com.example.travelhelper_server.vo.KnowledgeSourceVO;
import com.example.travelhelper_server.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/sources")
@RequiredArgsConstructor
public class KnowledgeSourceController {
    private final KnowledgeSourceService sourceService;

    @GetMapping
    public Result<List<KnowledgeSourceVO>> list() {
        return Result.ok(sourceService.list());
    }

    @PutMapping("/{sourceId}")
    public Result<KnowledgeSourceVO> update(@PathVariable String sourceId,
                                             @Valid @RequestBody KnowledgeSourceUpdateDTO request) {
        try {
            return Result.ok(sourceService.update(sourceId, request.enabled(), request.refreshPolicy()));
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }

    @PostMapping("/{sourceId}/refresh")
    public Result<KnowledgeRefreshResultVO> refresh(@PathVariable String sourceId) {
        try {
            return Result.ok(sourceService.refresh(sourceId));
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, error.getMessage(), error);
        }
    }
}
