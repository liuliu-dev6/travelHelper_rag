package com.example.travelhelper_server.intent;

import java.util.List;

public record QueryIntent(
        IntentType primaryIntent,
        List<IntentType> secondaryIntents,
        QuerySlots slots,
        QueryComplexity complexity,
        RetrievalNeeds needs,
        double confidence,
        List<String> missingSlots,
        String reason
) {
    public QueryIntent normalized() {
        return new QueryIntent(primaryIntent,
                secondaryIntents == null ? List.of() : secondaryIntents.stream()
                        .filter(intent -> intent != null && intent != primaryIntent)
                        .distinct()
                        .toList(),
                slots == null ? QuerySlots.empty() : slots.normalized(),
                complexity,
                needs,
                confidence,
                missingSlots == null ? List.of() : missingSlots.stream()
                        .filter(item -> item != null && !item.isBlank())
                        .distinct()
                        .toList(),
                reason == null ? "" : reason.strip());
    }
}
