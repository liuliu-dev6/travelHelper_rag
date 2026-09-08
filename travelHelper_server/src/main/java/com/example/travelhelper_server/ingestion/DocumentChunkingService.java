package com.example.travelhelper_server.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 标题、列表和表格感知的父子分块器。 */
@Service
public class DocumentChunkingService {
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern CHINESE_HEADING = Pattern.compile(
            "^(第[一二三四五六七八九十百0-9]+[章节篇]|[一二三四五六七八九十]+[、．.])\\s*(.+)$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^(\\d+(?:\\.\\d+)+)[、．.]?\\s*(.+)$");
    private static final Pattern LIST_ITEM = Pattern.compile(
            "^\\s*(?:[-*+]\\s+|\\d+[、．.)]\\s*|[（(]?[一二三四五六七八九十]+[）)、．.]\\s*).+");
    private static final List<String> BOUNDARIES = List.of("\n\n", "\n", "。", "！", "？", "；");

    private final int parentSize;
    private final int childSize;
    private final int childOverlap;
    private final int minChildSize;

    public DocumentChunkingService(
            @Value("${knowledge.ingestion.parent-chunk-size:1800}") int parentSize,
            @Value("${knowledge.ingestion.child-chunk-size:450}") int childSize,
            @Value("${knowledge.ingestion.child-chunk-overlap:80}") int childOverlap,
            @Value("${knowledge.ingestion.min-child-chunk-size:80}") int minChildSize) {
        if (parentSize < 600 || childSize < 200 || childSize >= parentSize
                || childOverlap < 0 || childOverlap >= childSize || minChildSize < 20) {
            throw new IllegalArgumentException("父子知识分块参数无效");
        }
        this.parentSize = parentSize;
        this.childSize = childSize;
        this.childOverlap = childOverlap;
        this.minChildSize = minChildSize;
    }

    /** 兼容旧调用：返回实际用于召回的小块。 */
    public List<String> chunk(String text) {
        return chunkStructured(text).parents().stream()
                .flatMap(parent -> parent.children().stream()).map(ChildChunkDraft::content).toList();
    }

    public StructuredChunks chunkStructured(String text) {
        if (text == null || text.isBlank()) return new StructuredChunks(List.of());
        List<Section> sections = sections(text);
        List<ParentChunkDraft> parents = new ArrayList<>();
        int parentIndex = 0;
        for (Section section : sections) {
            for (List<Block> group : parentGroups(section.blocks())) {
                String path = section.path().isBlank() ? "正文" : section.path();
                String parentContent = withHeading(path, joinBlocks(group));
                List<ChildChunkDraft> children = childChunks(path, group);
                if (!children.isEmpty()) {
                    parents.add(new ParentChunkDraft(parentIndex++, path, parentContent, children));
                }
            }
        }
        if (parents.isEmpty()) {
            List<String> fallback = window(text.strip(), childSize, childOverlap);
            parents.add(new ParentChunkDraft(0, "正文", text.strip(),
                    fallback.stream().map(value -> new ChildChunkDraft("正文", value)).toList()));
        }
        return new StructuredChunks(List.copyOf(parents));
    }

    private List<Section> sections(String text) {
        List<Section> result = new ArrayList<>();
        String[] headings = new String[6];
        String currentPath = "正文";
        List<Block> blocks = new ArrayList<>();
        BlockType activeType = null;
        StringBuilder active = new StringBuilder();
        for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String line = rawLine.stripTrailing();
            Heading heading = heading(line.strip());
            if (heading != null) {
                flushBlock(blocks, activeType, active);
                if (!blocks.isEmpty()) result.add(new Section(currentPath, List.copyOf(blocks)));
                blocks = new ArrayList<>();
                Arrays.fill(headings, heading.level(), headings.length, null);
                headings[heading.level() - 1] = heading.title();
                currentPath = Arrays.stream(headings).filter(value -> value != null && !value.isBlank())
                        .reduce((left, right) -> left + " > " + right).orElse(heading.title());
                activeType = null;
                continue;
            }
            if (line.isBlank()) {
                flushBlock(blocks, activeType, active);
                activeType = null;
                continue;
            }
            BlockType type = blockType(line);
            if (activeType != null && activeType != type) flushBlock(blocks, activeType, active);
            activeType = type;
            if (!active.isEmpty()) active.append('\n');
            active.append(line.strip());
        }
        flushBlock(blocks, activeType, active);
        if (!blocks.isEmpty()) result.add(new Section(currentPath, List.copyOf(blocks)));
        return result;
    }

    private Heading heading(String line) {
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) return new Heading(markdown.group(1).length(), markdown.group(2).strip());
        Matcher numbered = NUMBERED_HEADING.matcher(line);
        if (numbered.matches() && line.length() <= 80) {
            int level = Math.min(6, numbered.group(1).split("\\.").length);
            return new Heading(level, numbered.group(2).strip());
        }
        Matcher chinese = CHINESE_HEADING.matcher(line);
        if (chinese.matches() && line.length() <= 80 && !line.endsWith("。")) {
            return new Heading(chinese.group(1).startsWith("第") ? 1 : 2, chinese.group(2).strip());
        }
        return null;
    }

    private BlockType blockType(String line) {
        if (isTableLine(line)) return BlockType.TABLE;
        if (LIST_ITEM.matcher(line).matches()) return BlockType.LIST;
        return BlockType.PARAGRAPH;
    }

    private boolean isTableLine(String line) {
        long pipes = line.chars().filter(value -> value == '|').count();
        long tabs = line.chars().filter(value -> value == '\t').count();
        return pipes >= 2 || tabs >= 2 || line.matches(".*\\S\\s{2,}\\S.*")
                || line.toLowerCase(Locale.ROOT).matches("^\\s*[:|-]{3,}.*");
    }

    private void flushBlock(List<Block> blocks, BlockType type, StringBuilder value) {
        if (value.isEmpty()) return;
        blocks.add(new Block(type == null ? BlockType.PARAGRAPH : type, value.toString().strip()));
        value.setLength(0);
    }

    private List<List<Block>> parentGroups(List<Block> blocks) {
        List<List<Block>> groups = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        int length = 0;
        for (Block block : blocks) {
            List<Block> parts = block.content().length() <= parentSize
                    ? List.of(block)
                    : splitBlock(block, parentSize, 0).stream().map(value -> new Block(block.type(), value)).toList();
            for (Block part : parts) {
                int addition = part.content().length() + (current.isEmpty() ? 0 : 2);
                if (!current.isEmpty() && length + addition > parentSize) {
                    groups.add(List.copyOf(current));
                    current = new ArrayList<>();
                    length = 0;
                }
                current.add(part);
                length += part.content().length() + (current.size() == 1 ? 0 : 2);
            }
        }
        if (!current.isEmpty()) groups.add(List.copyOf(current));
        return groups;
    }

    private List<ChildChunkDraft> childChunks(String path, List<Block> blocks) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (Block block : blocks) {
            if (block.content().length() > childSize) {
                flushChild(values, current);
                values.addAll(splitBlock(block, childSize, childOverlap));
                continue;
            }
            int projected = current.length() + (current.isEmpty() ? 0 : 2) + block.content().length();
            if (!current.isEmpty() && projected > childSize) flushChild(values, current);
            if (!current.isEmpty()) current.append("\n\n");
            current.append(block.content());
        }
        flushChild(values, current);
        if (values.size() > 1 && values.getLast().length() < minChildSize) {
            String tail = values.removeLast();
            values.set(values.size() - 1, values.getLast() + "\n\n" + tail);
        }
        return values.stream().filter(value -> !value.isBlank())
                .map(value -> new ChildChunkDraft(path, withHeading(path, value))).toList();
    }

    private List<String> splitBlock(Block block, int size, int overlap) {
        if (block.type() == BlockType.TABLE) return tableGroups(block.content(), size);
        if (block.type() == BlockType.LIST) return lineGroups(block.content(), size);
        return window(block.content(), size, overlap);
    }

    /** 大表分片时重复表头，使任一小块都保留列含义。 */
    private List<String> tableGroups(String content, int size) {
        List<String> lines = content.lines().filter(line -> !line.isBlank()).toList();
        if (lines.size() <= 1) return lineGroups(content, size);
        List<String> header = new ArrayList<>();
        header.add(lines.getFirst());
        int dataStart = 1;
        if (lines.size() > 1 && lines.get(1).matches("^\\s*\\|?\\s*[:|-]{3,}.*")) {
            header.add(lines.get(1));
            dataStart = 2;
        }
        String headerText = String.join("\n", header);
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder(headerText);
        for (int index = dataStart; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.length() > size) {
                if (current.length() > headerText.length()) flushChild(result, current);
                for (String part : window(line, Math.max(1, size - headerText.length() - 1), 0)) {
                    result.add(headerText + "\n" + part);
                }
                current.setLength(0);
                current.append(headerText);
            } else if (current.length() > headerText.length()
                    && current.length() + 1 + line.length() > size) {
                flushChild(result, current);
                current.append(headerText);
                current.append('\n').append(line);
            } else {
                current.append('\n').append(line);
            }
        }
        if (current.length() > headerText.length() || result.isEmpty()) flushChild(result, current);
        return result;
    }

    /** 列表项和表格行是最小原子单元；只有单行本身超长时才退回句子窗口。 */
    private List<String> lineGroups(String content, int size) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.lines().toList()) {
            if (line.length() > size) {
                flushChild(result, current);
                result.addAll(window(line, size, 0));
            } else if (!current.isEmpty() && current.length() + 1 + line.length() > size) {
                flushChild(result, current);
                current.append(line);
            } else {
                if (!current.isEmpty()) current.append('\n');
                current.append(line);
            }
        }
        flushChild(result, current);
        return result;
    }

    private List<String> window(String text, int size, int overlap) {
        List<String> values = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(text.length(), start + size);
            int end = hardEnd == text.length() ? hardEnd : boundary(text, start, hardEnd, size);
            if (end <= start) end = hardEnd;
            String value = text.substring(start, end).strip();
            if (!value.isBlank()) values.add(value);
            if (end >= text.length()) break;
            start = skipWhitespace(text, Math.max(start + 1, end - overlap));
        }
        return values;
    }

    private int boundary(String text, int start, int hardEnd, int size) {
        int floor = Math.min(hardEnd, start + Math.max(minChildSize, size * 2 / 3));
        for (String marker : BOUNDARIES) {
            int index = text.lastIndexOf(marker, hardEnd - 1);
            if (index >= floor) return index + marker.length();
        }
        return hardEnd;
    }

    private void flushChild(List<String> result, StringBuilder value) {
        if (!value.isEmpty()) result.add(value.toString().strip());
        value.setLength(0);
    }

    private int skipWhitespace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        return index;
    }

    private String withHeading(String path, String value) {
        if (path == null || path.isBlank() || "正文".equals(path)) return value.strip();
        return "【" + path + "】\n" + value.strip();
    }

    private String joinBlocks(List<Block> blocks) {
        return blocks.stream().map(Block::content).reduce((left, right) -> left + "\n\n" + right).orElse("");
    }

    private enum BlockType { PARAGRAPH, LIST, TABLE }
    private record Block(BlockType type, String content) {}
    private record Heading(int level, String title) {}
    private record Section(String path, List<Block> blocks) {}

    public record StructuredChunks(List<ParentChunkDraft> parents) {
        public int childCount() {
            return parents.stream().mapToInt(parent -> parent.children().size()).sum();
        }
    }
    public record ParentChunkDraft(int parentIndex, String sectionPath, String content,
                                   List<ChildChunkDraft> children) {}
    public record ChildChunkDraft(String sectionPath, String content) {}
}
