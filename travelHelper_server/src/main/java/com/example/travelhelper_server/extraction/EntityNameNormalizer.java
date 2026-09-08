package com.example.travelhelper_server.extraction;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Map;

@Component
public class EntityNameNormalizer {
    private static final Map<String, String> KNOWN_ALIASES = Map.of(
            "湖南省博物馆", "湖南博物院",
            "湖南博物馆", "湖南博物院",
            "张家界国家森林公园景区", "张家界国家森林公园"
    );

    public String canonicalName(GraphEntityType type, String value) {
        String text = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        // 查询通常是完整句子，别名可能只是其中一段，因此不能只处理整串完全相等。
        for (Map.Entry<String, String> alias : KNOWN_ALIASES.entrySet()) {
            text = text.replace(alias.getKey(), alias.getValue());
        }
        text = text.replaceAll("[\\s·•・,，。.!！?？()（）]+", "");
        if (type == GraphEntityType.POI) {
            text = text.replaceFirst("(?:旅游景区|风景名胜区|风景区|旅游区|景区)$", "");
        }
        return text.toLowerCase();
    }

    public String canonicalKey(GraphEntityType type, String city, String name) {
        return type.name().toLowerCase() + "|" + canonicalName(GraphEntityType.CITY, city)
                + "|" + canonicalName(type, name);
    }
}
