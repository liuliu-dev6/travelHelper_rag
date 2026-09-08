package com.example.travelhelper_server.intent;

public record IntentClassification(QueryIntent intent, Source source) {
    public enum Source {
        LLM,
        LLM_RETRY,
        LOCAL_RULES,
        SAFE_DEFAULT
    }
}
