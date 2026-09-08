package com.example.travelhelper_server.controller;

import com.example.travelhelper_server.dto.KnowledgeUrlRequestDTO;
import com.example.travelhelper_server.ingestion.KnowledgeIngestionService;
import com.example.travelhelper_server.ingestion.DocumentQualityException;
import com.example.travelhelper_server.vo.KnowledgeDocumentVO;
import com.example.travelhelper_server.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {
    private final KnowledgeIngestionService ingestionService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Result<List<KnowledgeDocumentVO>> upload(@RequestPart("files") MultipartFile[] files,
                                                     @RequestParam(required = false) String city,
                                                     @RequestParam(required = false) String knowledgeType) {
        if (files == null || files.length == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择文件");
        List<KnowledgeDocumentVO> result = new ArrayList<>();
        try {
            for (MultipartFile file : files) result.add(ingestionService.ingest(file, city, knowledgeType));
            return Result.ok(result);
        } catch (DocumentQualityException error) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, error.getMessage(), error);
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "文档摄取失败：" + rootMessage(error), error);
        }
    }

    @PostMapping("/url")
    public Result<KnowledgeDocumentVO> url(@Valid @RequestBody KnowledgeUrlRequestDTO request) {
        try {
            return Result.ok(ingestionService.ingestUrl(request.url(), request.title(),
                    request.city(), request.knowledgeType()));
        } catch (DocumentQualityException error) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, error.getMessage(), error);
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "网页摄取失败：" + rootMessage(error), error);
        }
    }

    @GetMapping
    public Result<List<KnowledgeDocumentVO>> list() { return Result.ok(ingestionService.list()); }

    @DeleteMapping("/{documentId}")
    public Result<Void> delete(@PathVariable String documentId) {
        try {
            ingestionService.delete(documentId);
            return Result.ok();
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "删除知识文档失败：" + rootMessage(error), error);
        }
    }

    private String rootMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? "未知错误" : error.getMessage();
    }
}
