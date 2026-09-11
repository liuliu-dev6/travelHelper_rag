package com.example.travelhelper_server.ingestion;

/** 文档原文件已保留，但解析质量不足，禁止进入Embedding和检索库。 */
public class DocumentQualityException extends IllegalArgumentException {
    private final KnowledgeDocumentStatus status;

    public DocumentQualityException(KnowledgeDocumentStatus status, String message) {
        super(message);
        if (status != KnowledgeDocumentStatus.REVIEW_REQUIRED
                && status != KnowledgeDocumentStatus.PARSE_REVIEW
                && status != KnowledgeDocumentStatus.FAILED) {
            throw new IllegalArgumentException("文档质量异常状态必须是待审核或失败");
        }
        this.status = status;
    }

    public KnowledgeDocumentStatus status() {
        return status;
    }
}
