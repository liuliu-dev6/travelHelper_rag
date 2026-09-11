package com.example.travelhelper_server.ingestion;

public enum KnowledgeDocumentStatus {
    PROCESSING,
    REVIEW_REQUIRED,
    REJECTED,
    PARSE_REVIEW,
    INDEXED,
    FAILED
}
