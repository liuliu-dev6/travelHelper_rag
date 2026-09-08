package com.example.travelhelper_server.intent;

import java.util.List;

public record QuerySlots(
        List<String> cities,
        List<String> poiNames,
        List<String> foodNames,
        List<String> themes,
        List<String> companions,
        Integer days,
        String dateText,
        Integer budget,
        String transport,
        List<String> constraints
) {
    public QuerySlots normalized() {
        return new QuerySlots(list(cities), list(poiNames), list(foodNames), list(themes),
                list(companions), days, blankToNull(dateText), budget, blankToNull(transport),
                list(constraints));
    }

    public static QuerySlots empty() {
        return new QuerySlots(List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, List.of());
    }

    private static List<String> list(List<String> value) {
        return value == null ? List.of() : value.stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
