package com.example.travelhelper_server.service;

import com.example.travelhelper_server.entity.User;
import com.example.travelhelper_server.entity.UserPreference;
import com.example.travelhelper_server.intent.QueryIntent;
import com.example.travelhelper_server.repository.UserPreferenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private static final List<String> NEGATIVE_OUTDOOR = List.of("爬山", "登山", "徒步", "长距离步行");
    private final UserPreferenceRepository repository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PreferenceSnapshot get(String username) {
        User user = authService.requireUser(username);
        return repository.findByUserId(user.getId()).map(this::snapshot).orElse(PreferenceSnapshot.empty());
    }

    @Transactional
    public PreferenceSnapshot remember(String username, QueryIntent intent, String message) {
        User user = authService.requireUser(username);
        UserPreference entity = repository.findByUserId(user.getId()).orElseGet(() -> {
            UserPreference created = new UserPreference();
            created.setUser(user);
            return created;
        });

        Set<String> preferred = new LinkedHashSet<>(readList(entity.getPreferredThemes()));
        Set<String> avoided = new LinkedHashSet<>(readList(entity.getAvoidedThemes()));
        Set<String> companions = new LinkedHashSet<>(readList(entity.getCompanions()));
        boolean negative = containsAny(message, List.of("不喜欢", "不要", "别再", "避免", "不想"));

        for (String theme : intent.slots().themes()) {
            if (negative) {
                avoided.add(theme);
                preferred.remove(theme);
            } else {
                preferred.add(theme);
                avoided.remove(theme);
            }
        }
        companions.addAll(intent.slots().companions());
        if (intent.slots().budget() != null) entity.setMaxBudget(intent.slots().budget());

        if (containsAny(message, List.of("少走路", "走不动", "步行能力差", "不爬山"))
                || intent.slots().companions().stream().anyMatch(item -> item.contains("老人"))) {
            entity.setWalkingAbility("LOW");
            avoided.addAll(NEGATIVE_OUTDOOR);
        } else if (containsAny(message, List.of("喜欢徒步", "喜欢爬山", "体力很好"))) {
            entity.setWalkingAbility("HIGH");
        }

        // 规则分类未必能把自由表达放入 themes，这里保留常用偏好词。
        for (String theme : List.of("亲子", "情侣", "自然", "历史", "文化", "夜景", "拍照", "小众", "室内", "免费")) {
            if (!message.contains(theme)) continue;
            if (negative) {
                avoided.add(theme);
                preferred.remove(theme);
            } else {
                preferred.add(theme);
                avoided.remove(theme);
            }
        }

        entity.setPreferredThemes(writeList(preferred));
        entity.setAvoidedThemes(writeList(avoided));
        entity.setCompanions(writeList(companions));
        return snapshot(repository.save(entity));
    }

    public String enrichQuery(String query, PreferenceSnapshot preference) {
        if (preference.isEmpty()) return query;
        StringBuilder result = new StringBuilder(query).append("。用户长期偏好：");
        if (!preference.preferredThemes().isEmpty()) result.append("喜欢").append(preference.preferredThemes()).append('；');
        if (!preference.companions().isEmpty()) result.append("常见同行人").append(preference.companions()).append('；');
        if (preference.walkingAbility() != null) result.append("步行能力").append(preference.walkingAbility()).append('；');
        if (!preference.avoidedThemes().isEmpty()) result.append("避免").append(preference.avoidedThemes()).append('；');
        return result.toString();
    }

    /** 返回一个很小的排序增量；主召回仍由 RRF 排名决定。 */
    public double rankingBoost(Map<String, Object> hit, PreferenceSnapshot preference) {
        if (preference.isEmpty()) return 0;
        List<String> tags = hit.get("tags") instanceof List<?> values
                ? values.stream().map(String::valueOf).toList() : List.of();
        String text = String.valueOf(hit.getOrDefault("name", "")) + " "
                + String.valueOf(hit.getOrDefault("description", "")) + " " + tags;
        double boost = 0;
        for (String value : preference.preferredThemes()) if (text.contains(value)) boost += 0.0025;
        for (String value : preference.avoidedThemes()) if (text.contains(value)) boost -= 0.006;
        if (preference.companions().stream().anyMatch(item -> item.contains("孩子") || item.contains("儿童"))
                && text.contains("亲子")) boost += 0.004;
        if (preference.walkingAbility() != null && preference.walkingAbility().equals("LOW")
                && containsAny(text, NEGATIVE_OUTDOOR)) boost -= 0.008;
        return boost;
    }

    public String summary(PreferenceSnapshot snapshot) {
        return "已更新偏好：喜欢=" + snapshot.preferredThemes()
                + "，避免=" + snapshot.avoidedThemes()
                + "，同行人=" + snapshot.companions()
                + (snapshot.maxBudget() == null ? "" : "，预算=" + snapshot.maxBudget() + "元")
                + (snapshot.walkingAbility() == null ? "" : "，步行能力=" + snapshot.walkingAbility());
    }

    private PreferenceSnapshot snapshot(UserPreference entity) {
        return new PreferenceSnapshot(readList(entity.getPreferredThemes()), readList(entity.getAvoidedThemes()),
                readList(entity.getCompanions()), entity.getMaxBudget(), entity.getWalkingAbility());
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeList(Iterable<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("用户偏好序列化失败", e);
        }
    }

    private static boolean containsAny(String text, List<String> values) {
        return values.stream().anyMatch(text::contains);
    }

    public record PreferenceSnapshot(
            List<String> preferredThemes,
            List<String> avoidedThemes,
            List<String> companions,
            Integer maxBudget,
            String walkingAbility
    ) {
        public static PreferenceSnapshot empty() {
            return new PreferenceSnapshot(List.of(), List.of(), List.of(), null, null);
        }

        public boolean isEmpty() {
            return preferredThemes.isEmpty() && avoidedThemes.isEmpty() && companions.isEmpty()
                    && maxBudget == null && walkingAbility == null;
        }
    }
}
