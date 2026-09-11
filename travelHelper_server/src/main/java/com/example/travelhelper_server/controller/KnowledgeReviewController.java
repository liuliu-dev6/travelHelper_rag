package com.example.travelhelper_server.controller;

import com.example.travelhelper_server.dto.ReviewDecisionDTO;
import com.example.travelhelper_server.extraction.GraphExtractionService;
import com.example.travelhelper_server.ingestion.KnowledgeIngestionService;
import com.example.travelhelper_server.vo.GraphCandidateVO;
import com.example.travelhelper_server.vo.KnowledgeDocumentVO;
import com.example.travelhelper_server.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge/reviews")
@RequiredArgsConstructor
public class KnowledgeReviewController {
    private final KnowledgeIngestionService ingestionService;
    private final GraphExtractionService graphExtractionService;

    @GetMapping("/documents")
    public Result<List<KnowledgeDocumentVO>> documents() {
        return Result.ok(ingestionService.listReviewRequired());
    }

    @PostMapping("/documents/{documentId}/approve")
    public Result<KnowledgeDocumentVO> approveDocument(@PathVariable String documentId, Principal principal) {
        try { return Result.ok(ingestionService.approveReview(documentId, principal.getName())); }
        catch (Exception error) { throw failure("文档审核发布失败", error); }
    }

    @PostMapping("/documents/{documentId}/reject")
    public Result<KnowledgeDocumentVO> rejectDocument(@PathVariable String documentId,
            @Valid @RequestBody(required = false) ReviewDecisionDTO decision, Principal principal) {
        try {
            return Result.ok(ingestionService.rejectReview(documentId, principal.getName(),
                    decision == null ? null : decision.reason()));
        } catch (Exception error) { throw failure("文档审核拒绝失败", error); }
    }

    @GetMapping("/relations")
    public Result<List<GraphCandidateVO>> relations() {
        return Result.ok(graphExtractionService.pendingRelations());
    }

    @PostMapping("/relations/{candidateId}/approve")
    public Result<GraphCandidateVO> approveRelation(@PathVariable String candidateId, Principal principal) {
        try { return Result.ok(graphExtractionService.approveRelation(candidateId, principal.getName())); }
        catch (Exception error) { throw failure("图谱候选发布失败", error); }
    }

    @PostMapping("/relations/{candidateId}/reject")
    public Result<Void> rejectRelation(@PathVariable String candidateId,
            @Valid @RequestBody(required = false) ReviewDecisionDTO decision, Principal principal) {
        try {
            graphExtractionService.rejectRelation(candidateId, principal.getName(),
                    decision == null ? null : decision.reason());
            return Result.ok();
        } catch (Exception error) { throw failure("图谱候选拒绝失败", error); }
    }

    private ResponseStatusException failure(String prefix, Exception error) {
        String message = error.getMessage() == null || error.getMessage().isBlank() ? "未知错误" : error.getMessage();
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, prefix + "：" + message, error);
    }
}
