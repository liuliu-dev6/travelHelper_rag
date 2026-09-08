package com.example.travelhelper_server.ingestion;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 解析质量门禁：只判断是否可安全索引，不自动修补可能已失真的事实文本。 */
@Service
public class DocumentQualityService {
    private static final double REPLACEMENT_LIMIT = 0.005;
    private static final double CONTROL_LIMIT = 0.005;
    private static final double PRIVATE_USE_LIMIT = 0.005;
    private static final double READABLE_LIMIT = 0.75;
    private static final Set<String> TOURISM_TERMS = Set.of(
            "旅游", "景区", "景点", "博物馆", "公园", "门票", "开放", "游览", "交通", "酒店", "美食", "展览");
    private static final List<String> MOJIBAKE_MARKERS = List.of("锟斤拷", "Ã", "Â", "æ", "å");
    private static final String COMMON_PUNCTUATION =
            "，。；：、！？,.!?;:()（）[]【】《》“”‘’\"'%％-—–_/\\+¥￥#&·…<>=\n\r\t";

    public QualityReport inspect(DocumentParserService.ParsedDocument document) {
        String text = Normalizer.normalize(document.text() == null ? "" : document.text(),
                Normalizer.Form.NFKC);
        int[] points = text.codePoints().toArray();
        int total = Math.max(1, points.length);
        int replacements = 0;
        int controls = 0;
        int privateUse = 0;
        int readable = 0;
        int han = 0;
        for (int point : points) {
            if (point == 0xFFFD) replacements++;
            int type = Character.getType(point);
            if (type == Character.CONTROL && !allowedControl(point)) controls++;
            if (type == Character.PRIVATE_USE) privateUse++;
            if (isReadable(point)) readable++;
            if (Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN) han++;
        }

        List<String> hardFailures = new ArrayList<>();
        List<String> reviewReasons = new ArrayList<>();
        double replacementRatio = ratio(replacements, total);
        double controlRatio = ratio(controls, total);
        double privateUseRatio = ratio(privateUse, total);
        double readableRatio = ratio(readable, total);
        if (replacementRatio > REPLACEMENT_LIMIT) {
            hardFailures.add("替换字符U+FFFD占比" + percent(replacementRatio) + "，超过0.5%");
        }
        if (controlRatio > CONTROL_LIMIT) {
            hardFailures.add("非法控制字符占比" + percent(controlRatio) + "，超过0.5%");
        }
        if (privateUseRatio > PRIVATE_USE_LIMIT) {
            hardFailures.add("私有区字符占比" + percent(privateUseRatio) + "，超过0.5%");
        }
        if (isChineseTourismDocument(text, han) && readableRatio < READABLE_LIMIT) {
            hardFailures.add("中文旅游文档可读字符占比仅" + percent(readableRatio));
        }
        String repeated = repeatedPattern(text);
        if (repeated != null) hardFailures.add("检测到异常重复片段：" + repeated);

        List<String> markers = MOJIBAKE_MARKERS.stream().filter(text::contains).toList();
        if (!markers.isEmpty()) reviewReasons.add("疑似错误编码片段：" + String.join("、", markers));
        if (document.pdf()) inspectPdf(document.pages(), reviewReasons);

        Decision decision = !hardFailures.isEmpty() ? Decision.FAILED
                : !reviewReasons.isEmpty() ? Decision.PARSE_REVIEW : Decision.PASS;
        List<String> issues = new ArrayList<>(hardFailures);
        issues.addAll(reviewReasons);
        return new QualityReport(decision, List.copyOf(issues), replacementRatio, controlRatio,
                privateUseRatio, readableRatio, document.pages().size());
    }

    public void requireIndexable(DocumentParserService.ParsedDocument document) {
        QualityReport report = inspect(document);
        if (report.decision() == Decision.PASS) return;
        KnowledgeDocumentStatus status = report.decision() == Decision.FAILED
                ? KnowledgeDocumentStatus.FAILED : KnowledgeDocumentStatus.PARSE_REVIEW;
        String prefix = status == KnowledgeDocumentStatus.FAILED
                ? "文档疑似存在编码、字体映射或异常字符问题"
                : "文档疑似存在编码、扫描件或版面阅读顺序问题";
        throw new DocumentQualityException(status, prefix + "，有效文本质量未通过，已保留原文件并阻止进入知识库。原因："
                + String.join("；", report.issues()));
    }

    private void inspectPdf(List<String> pages, List<String> reviewReasons) {
        int pageCount = pages.size();
        if (pageCount == 0) {
            reviewReasons.add("PDF未获得逐页文本，无法验证阅读顺序");
            return;
        }
        int totalVisible = pages.stream().mapToInt(this::visibleCharacters).sum();
        long sparsePages = pages.stream().filter(page -> visibleCharacters(page) < 20).count();
        if (pageCount >= 5 && (double) totalVisible / pageCount < 40) {
            reviewReasons.add("PDF共" + pageCount + "页但平均每页有效文字少于40字，疑似扫描件");
        } else if (pageCount >= 4 && sparsePages * 2 >= pageCount) {
            reviewReasons.add("PDF超过一半页面提取文字少于20字，疑似扫描页或字体映射缺失");
        }
        for (int index = 0; index < pages.size(); index++) {
            if (abnormalLineDistribution(pages.get(index))) {
                reviewReasons.add("PDF第" + (index + 1) + "页行长分布异常，可能存在双栏、竖排或表格阅读顺序问题");
                break;
            }
        }
    }

    /** 纯文本无法证明语义顺序，只用保守启发式把高风险版面送人工复核。 */
    private boolean abnormalLineDistribution(String page) {
        List<Integer> lengths = page.lines().map(String::strip).filter(line -> !line.isBlank())
                .map(line -> line.codePointCount(0, line.length())).toList();
        if (lengths.size() < 16) return false;
        long tiny = lengths.stream().filter(length -> length <= 2).count();
        long shortLines = lengths.stream().filter(length -> length <= 4).count();
        int longest = lengths.stream().mapToInt(Integer::intValue).max().orElse(0);
        return tiny * 100 >= lengths.size() * 35
                || shortLines * 100 >= lengths.size() * 50 && longest >= 30;
    }

    private String repeatedPattern(String text) {
        String compact = text.replaceAll("\\s+", "");
        int[] points = compact.codePoints().toArray();
        for (int start = 0; start < points.length;) {
            int end = start + 1;
            while (end < points.length && points[end] == points[start]) end++;
            if (end - start >= 12 && Character.isLetterOrDigit(points[start])) {
                return new String(Character.toChars(points[start])) + "×" + (end - start);
            }
            start = end;
        }
        for (int unit = 2; unit <= 6; unit++) {
            for (int start = 0; start + unit * 8 <= compact.length(); start++) {
                String value = compact.substring(start, start + unit);
                if (value.codePoints().noneMatch(Character::isLetterOrDigit)) continue;
                int repeats = 1;
                while (start + (repeats + 1) * unit <= compact.length()
                        && compact.regionMatches(start, compact, start + repeats * unit, unit)) repeats++;
                if (repeats >= 8) return abbreviate(value) + "×" + repeats;
            }
        }
        return null;
    }

    private boolean isChineseTourismDocument(String text, int hanCount) {
        return hanCount >= 20 && TOURISM_TERMS.stream().anyMatch(text::contains);
    }

    private boolean isReadable(int point) {
        if (Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN) return true;
        if (point < 128 && (Character.isLetterOrDigit(point) || Character.isWhitespace(point))) return true;
        return COMMON_PUNCTUATION.indexOf(point) >= 0;
    }

    private boolean allowedControl(int point) {
        return point == '\n' || point == '\r' || point == '\t' || point == '\f';
    }

    private int visibleCharacters(String value) {
        return value == null ? 0 : (int) value.codePoints().filter(point -> !Character.isWhitespace(point)).count();
    }

    private double ratio(int value, int total) { return (double) value / Math.max(1, total); }
    private String percent(double value) { return String.format(Locale.ROOT, "%.2f%%", value * 100); }
    private String abbreviate(String value) { return value.length() <= 12 ? value : value.substring(0, 12); }

    public enum Decision { PASS, PARSE_REVIEW, FAILED }

    public record QualityReport(Decision decision, List<String> issues,
                                double replacementRatio, double controlRatio,
                                double privateUseRatio, double readableRatio, int pageCount) {}
}
