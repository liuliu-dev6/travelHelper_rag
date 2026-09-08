package com.example.travelhelper_server.ingestion;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DocumentCleaningService {
    private static final Pattern WEB_BOILERPLATE = Pattern.compile(
            "(?i)^(首页|home|更多[>》→]*|查看详情[>》→]*|返回顶部|友情链接|管理登录|在线客服|手机版|nav)$");

    public String clean(String raw) {
        if (raw == null) return "";
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace('\u00a0', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replace("\r\n", "\n").replace('\r', '\n')
                // 保留Tab和连续空格：它们可能是PDF/纯文本表格的列边界。
                .replaceAll("[\\x0B\\f]+", " ");
        StringBuilder result = new StringBuilder();
        Set<String> consecutive = new LinkedHashSet<>();
        for (String rawLine : normalized.split("\\n")) {
            String line = rawLine.strip();
            if (line.isBlank()) {
                if (!result.isEmpty() && !result.toString().endsWith("\n\n")) result.append("\n\n");
                consecutive.clear();
                continue;
            }
            if (WEB_BOILERPLATE.matcher(line.replaceAll("\\s+", "")).matches()) continue;
            // PDF页眉页脚经常连续重复；只压缩当前段内完全相同的短行。
            if (line.length() <= 80 && !consecutive.add(line)) continue;
            result.append(line).append('\n');
        }
        return result.toString().replaceAll("\\n{3,}", "\n\n").strip();
    }
}
