package com.example.travelhelper_server.intent;

public record RetrievalNeeds(
        boolean vectorSearch,
        boolean graphSearch,
        boolean realtimeApi,
        boolean userMemory
) {
}
