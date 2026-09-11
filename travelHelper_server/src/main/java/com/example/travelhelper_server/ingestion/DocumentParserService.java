package com.example.travelhelper_server.ingestion;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentParserService {
    private static final Pattern WORD_HEADING = Pattern.compile("(?i).*(?:heading|标题)\\s*([1-6]).*");
    private final AutoDetectParser parser = new AutoDetectParser();

    public ParsedDocument parse(byte[] content, String fileName, String declaredMimeType) throws Exception {
        if (isHtml(fileName, declaredMimeType)) return parseHtml(content, declaredMimeType);
        if (isDocx(fileName, declaredMimeType)) return parseDocx(content);
        Metadata metadata = new Metadata();
        if (fileName != null && !fileName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        if (declaredMimeType != null && !declaredMimeType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, declaredMimeType);
        }
        BodyContentHandler handler = new BodyContentHandler(-1);
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            parser.parse(input, handler, metadata, new ParseContext());
        }
        String mimeType = metadata.get(Metadata.CONTENT_TYPE);
        String title = metadata.get(TikaCoreProperties.TITLE);
        String effectiveMime = mimeType == null ? declaredMimeType : mimeType;
        boolean pdf = isPdf(fileName, effectiveMime);
        return new ParsedDocument(handler.toString(), title, effectiveMime, pdf,
                pdf ? extractPdfPages(content) : List.of());
    }

    private ParsedDocument parseDocx(byte[] content) throws Exception {
        StringBuilder text = new StringBuilder();
        String title;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            title = document.getProperties().getCoreProperties().getTitle();
            for (IBodyElement bodyElement : document.getBodyElements()) {
                if (bodyElement instanceof XWPFParagraph paragraph) {
                    String value = paragraph.getText().strip();
                    if (value.isBlank()) {
                        appendBreak(text);
                        continue;
                    }
                    int headingLevel = headingLevel(document, paragraph);
                    if (headingLevel > 0) text.append("#".repeat(headingLevel)).append(' ');
                    else if (paragraph.getNumID() != null) text.append("- ");
                    text.append(value).append('\n');
                } else if (bodyElement instanceof XWPFTable table) {
                    table.getRows().forEach(row -> {
                        List<String> cells = row.getTableCells().stream().map(cell -> cell.getText().strip())
                                .filter(value -> !value.isBlank()).toList();
                        if (!cells.isEmpty()) text.append("| ").append(String.join(" | ", cells)).append(" |\n");
                    });
                    appendBreak(text);
                }
            }
        }
        return new ParsedDocument(text.toString(), title,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false, List.of());
    }

    private int headingLevel(XWPFDocument document, XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        String styleName = style;
        if (style != null && document.getStyles() != null) {
            XWPFStyle resolved = document.getStyles().getStyle(style);
            if (resolved != null && resolved.getName() != null) styleName = resolved.getName();
        }
        Matcher matcher = WORD_HEADING.matcher(styleName == null ? "" : styleName);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private void appendBreak(StringBuilder text) {
        if (!text.isEmpty() && !text.toString().endsWith("\n\n")) text.append('\n');
    }


    private ParsedDocument parseHtml(byte[] content, String declaredMimeType) throws Exception {
        Document document = Jsoup.parse(new ByteArrayInputStream(content), null, "");
        String title = document.title();
        document.select("script,style,noscript,svg,nav,header,footer,aside,form").remove();
        // 在丢弃DOM前把结构编码成纯文本标记，让后续分块器仍能识别标题层级、列表和表格行。
        for (Element row : new ArrayList<>(document.select("tr"))) {
            List<String> cells = row.select("th,td").eachText().stream().map(String::strip)
                    .filter(value -> !value.isBlank()).toList();
            if (!cells.isEmpty()) row.replaceWith(new TextNode("\n| " + String.join(" | ", cells) + " |\n"));
        }
        for (int level = 1; level <= 6; level++) {
            String prefix = "#".repeat(level) + " ";
            document.select("h" + level).forEach(heading -> heading.prependText(prefix));
        }
        document.select("li").forEach(item -> item.prependText("- "));
        document.select("br").append("\n");
        document.select("p,li,h1,h2,h3,h4,h5,h6,section,article").append("\n");
        return new ParsedDocument(document.body() == null ? "" : document.body().wholeText(), title,
                declaredMimeType == null ? "text/html" : declaredMimeType, false, List.of());
    }

    private boolean isHtml(String fileName, String mimeType) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        String type = mimeType == null ? "" : mimeType.toLowerCase();
        return type.contains("html") || name.endsWith(".html") || name.endsWith(".htm");
    }

    private boolean isPdf(String fileName, String mimeType) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        String type = mimeType == null ? "" : mimeType.toLowerCase();
        return type.contains("pdf") || name.endsWith(".pdf");
    }

    private boolean isDocx(String fileName, String mimeType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        return name.endsWith(".docx") || type.contains("wordprocessingml.document");
    }

    /** 逐页提取仅用于质量统计；sortByPosition可改善常规版面，但不等同于双栏语义重建。 */
    private List<String> extractPdfPages(byte[] content) throws Exception {
        List<String> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(content)) {
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
        }
        return List.copyOf(pages);
    }

    public record ParsedDocument(String text, String title, String mimeType,
                                 boolean pdf, List<String> pages) {
        public ParsedDocument {
            pages = pages == null ? List.of() : List.copyOf(pages);
        }
    }
}
