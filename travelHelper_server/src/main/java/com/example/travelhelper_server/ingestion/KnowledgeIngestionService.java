package com.example.travelhelper_server.ingestion;

import com.example.travelhelper_server.client.EmbeddingClient;
import com.example.travelhelper_server.entity.KnowledgeChunk;
import com.example.travelhelper_server.entity.KnowledgeDocument;
import com.example.travelhelper_server.entity.KnowledgeParentChunk;
import com.example.travelhelper_server.repository.KnowledgeChunkRepository;
import com.example.travelhelper_server.repository.KnowledgeDocumentRepository;
import com.example.travelhelper_server.repository.KnowledgeParentChunkRepository;
import com.example.travelhelper_server.service.Bm25SearchService;
import com.example.travelhelper_server.vector.QdrantClient;
import com.example.travelhelper_server.vo.KnowledgeDocumentVO;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class KnowledgeIngestionService {
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "html", "htm", "md", "markdown", "txt");
    private static final List<String> KNOWN_CITIES = List.of(
            "北京", "上海", "天津", "重庆", "广州", "深圳", "长沙", "张家界", "武汉", "南京", "苏州",
            "杭州", "成都", "西安", "昆明", "大理", "丽江", "贵阳", "桂林", "海口", "三亚", "厦门",
            "青岛", "济南", "郑州", "洛阳", "沈阳", "大连", "哈尔滨", "长春", "兰州", "西宁", "拉萨");

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeParentChunkRepository parentChunkRepository;
    private final DocumentParserService parser;
    private final DocumentQualityService qualityService;
    private final DocumentCleaningService cleaner;
    private final DocumentChunkingService chunker;
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;
    private final Bm25SearchService bm25SearchService;
    private final Path storageDir;
    private final int maxBytes;
    private final OkHttpClient webClient;

    public KnowledgeIngestionService(KnowledgeDocumentRepository documentRepository,
                                     KnowledgeChunkRepository chunkRepository,
                                     KnowledgeParentChunkRepository parentChunkRepository,
                                     DocumentParserService parser,
                                     DocumentQualityService qualityService,
                                     DocumentCleaningService cleaner,
                                     DocumentChunkingService chunker,
                                     EmbeddingClient embeddingClient,
                                     QdrantClient qdrantClient,
                                     Bm25SearchService bm25SearchService,
                                     @Value("${knowledge.ingestion.storage-dir:./data/knowledge-files}") String storageDir,
                                     @Value("${knowledge.ingestion.max-bytes:20971520}") int maxBytes) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.parentChunkRepository = parentChunkRepository;
        this.parser = parser;
        this.qualityService = qualityService;
        this.cleaner = cleaner;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.qdrantClient = qdrantClient;
        this.bm25SearchService = bm25SearchService;
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.webClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(40)).followRedirects(true).build();
    }

    public KnowledgeDocumentVO ingest(MultipartFile file, String city, String knowledgeType) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        String fileName = safeFileName(file.getOriginalFilename());
        validateExtension(fileName);
        if (file.getSize() > maxBytes) throw new IllegalArgumentException("单个文件不能超过 " + maxBytes / 1024 / 1024 + "MB");
        byte[] bytes = file.getBytes();
        Path stored = store(bytes, fileName);
        return process(bytes, fileName, file.getContentType(), KnowledgeSourceType.FILE,
                fileName, stored.toString(), city, knowledgeType, null, null);
    }

    public KnowledgeDocumentVO ingestUrl(String url, String requestedTitle,
                                         String city, String knowledgeType) throws Exception {
        UrlFetchResult fetched = fetchUrl(url, null, null);
        if (fetched.notModified()) throw new IllegalStateException("首次加载网页不应返回304");
        return ingestFetchedUrl(fetched, url, requestedTitle, city, knowledgeType, null);
    }

    /** 条件加载可信网页；304时不读取正文、不产生Embedding费用。 */
    public UrlFetchResult fetchUrl(String url, String etag, String lastModified) throws Exception {
        URI uri = validatePublicUrl(url);
        Request.Builder builder = new Request.Builder().url(uri.toURL())
                .header("User-Agent", "travelHelper-knowledge-sync/1.0");
        if (hasText(etag)) builder.header("If-None-Match", etag);
        if (hasText(lastModified)) builder.header("If-Modified-Since", lastModified);
        Request request = builder.build();
        try (Response response = webClient.newCall(request).execute()) {
            String resolvedUrl = response.request().url().toString();
            validatePublicUrl(resolvedUrl);
            if (response.code() == 304) {
                return new UrlFetchResult(null, resolvedUrl, null, null,
                        response.header("ETag", etag), response.header("Last-Modified", lastModified), true);
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalArgumentException("网页加载失败，HTTP " + response.code());
            }
            byte[] bytes = response.body().byteStream().readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new IllegalArgumentException("网页内容超过大小限制");
            String fileName = fileName(response.request().url().encodedPath(), "webpage.html");
            String contentType = response.header("Content-Type", "text/html");
            return new UrlFetchResult(bytes, resolvedUrl, fileName, contentType,
                    response.header("ETag"), response.header("Last-Modified"), false);
        }
    }

    /**
     * 加载栏目页，并仅跟进同域名、标题命中白名单关键词的少量详情页。
     * 这让订阅保存真正的公告正文，同时避免演变成无边界全站爬虫。
     */
    public UrlFetchResult fetchUrlBundle(String url, String etag, String lastModified,
                                         String crawlKeywords, int maxLinkedPages) throws Exception {
        UrlFetchResult landing = fetchUrl(url, etag, lastModified);
        if (landing.notModified() || maxLinkedPages <= 0 || landing.content() == null
                || !Objects.toString(landing.mimeType(), "").toLowerCase(Locale.ROOT).contains("html")) {
            return landing;
        }
        List<String> keywords = Arrays.stream(Objects.toString(crawlKeywords, "").split("\\s+"))
                .map(String::strip).filter(item -> !item.isBlank()).distinct().toList();
        if (keywords.isEmpty()) return landing;

        Document page = Jsoup.parse(new ByteArrayInputStream(landing.content()), null, landing.resolvedUrl());
        URI landingUri = URI.create(landing.resolvedUrl());
        LinkedHashMap<String, String> links = new LinkedHashMap<>();
        for (Element anchor : page.select("a[href]")) {
            String title = anchor.text().strip();
            if (title.length() < 4 || keywords.stream().noneMatch(title::contains)) continue;
            String absoluteUrl = anchor.absUrl("href");
            if (!hasText(absoluteUrl)) continue;
            try {
                URI linkUri = validatePublicUrl(absoluteUrl);
                if (!Objects.equals(normalizeHost(landingUri.getHost()), normalizeHost(linkUri.getHost()))) continue;
                links.putIfAbsent(linkUri.toString(), title);
            } catch (Exception ignored) {
                // 页面中的非法、内网或非HTTP链接不进入候选。
            }
            if (links.size() >= maxLinkedPages) break;
        }
        if (links.isEmpty()) return landing;

        StringBuilder bundle = new StringBuilder();
        for (Map.Entry<String, String> link : links.entrySet()) {
            try {
                UrlFetchResult article = fetchUrl(link.getKey(), null, null);
                if (article.notModified() || article.content() == null) continue;
                String text = extractHtmlText(article);
                if (text.length() < 50) continue;
                bundle.append("标题：").append(link.getValue()).append('\n')
                        .append("来源页面：").append(article.resolvedUrl()).append('\n')
                        .append(text).append("\n\n");
                if (bundle.toString().getBytes(StandardCharsets.UTF_8).length >= maxBytes) break;
            } catch (Exception ignored) {
                // 单篇公告失效不能阻断整个可信来源刷新。
            }
        }
        if (bundle.length() < 50) return landing;
        byte[] combined = bundle.toString().getBytes(StandardCharsets.UTF_8);
        if (combined.length > maxBytes) combined = Arrays.copyOf(combined, maxBytes);
        return new UrlFetchResult(combined, landing.resolvedUrl(), "trusted-source-bundle.txt",
                "text/plain; charset=utf-8", landing.etag(), landing.lastModified(), false);
    }

    private String extractHtmlText(UrlFetchResult article) throws Exception {
        Document document = Jsoup.parse(new ByteArrayInputStream(article.content()), null, article.resolvedUrl());
        document.select("script,style,noscript,svg,nav,footer").remove();
        Element best = null;
        for (Element candidate : document.select("article,main,.article,.article-content,.TRS_Editor,#content")) {
            if (best == null || candidate.text().length() > best.text().length()) best = candidate;
        }
        return cleaner.clean((best == null ? document.body() : best).text());
    }

    private String normalizeHost(String host) {
        return Objects.toString(host, "").toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }

    /** 将已经完成条件请求的网页内容进入现有清洗、分块、Embedding和Qdrant管道。 */
    public KnowledgeDocumentVO ingestFetchedUrl(UrlFetchResult fetched, String sourceUrl, String requestedTitle,
                                                 String city, String knowledgeType, String sourceId) throws Exception {
        if (fetched == null || fetched.notModified() || fetched.content() == null) {
            throw new IllegalArgumentException("网页正文为空，无法入库");
        }
        Path stored = store(fetched.content(), fetched.fileName());
        return process(fetched.content(), fetched.fileName(), fetched.mimeType(), KnowledgeSourceType.URL,
                sourceUrl, stored.toString(), city, knowledgeType, requestedTitle, sourceId);
    }

    public String contentChecksum(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }

    /** 对解析清洗后的正文计算指纹，避免HTML模板、属性顺序等无意义变化触发重新向量化。 */
    public String normalizedContentChecksum(UrlFetchResult fetched) throws Exception {
        DocumentParserService.ParsedDocument parsed = parser.parse(
                fetched.content(), fetched.fileName(), fetched.mimeType());
        return contentChecksum(cleaner.clean(parsed.text()).getBytes(StandardCharsets.UTF_8));
    }

    public List<KnowledgeDocumentVO> list() {
        return documentRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(KnowledgeDocumentVO::from).toList();
    }

    public void delete(String documentId) throws Exception {
        deleteInternal(documentId, false);
    }

    public void deleteSubscriptionVersion(String documentId) throws Exception {
        deleteInternal(documentId, true);
    }

    private void deleteInternal(String documentId, boolean allowSubscribed) throws Exception {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NoSuchElementException("知识文档不存在"));
        if (!allowSubscribed && hasText(document.getSourceId())) {
            throw new IllegalStateException("该文档由可信订阅维护，请在可信订阅页面管理来源");
        }
        List<String> pointIds = chunkRepository.findAllByDocumentIdOrderByChunkIndex(documentId).stream()
                .map(KnowledgeChunk::getQdrantPointId).toList();
        qdrantClient.deletePoints(pointIds);
        chunkRepository.deleteAllByDocumentId(documentId);
        parentChunkRepository.deleteAllByDocumentId(documentId);
        documentRepository.delete(document);
        if (document.getStoredPath() != null) Files.deleteIfExists(Path.of(document.getStoredPath()));
        bm25SearchService.invalidate();
    }

    private KnowledgeDocumentVO process(byte[] bytes, String fileName, String declaredMime,
                                        KnowledgeSourceType sourceType, String sourceUri, String storedPath,
                                        String city, String knowledgeType, String requestedTitle,
                                        String sourceId) throws Exception {
        String checksum = sha256(bytes, city, knowledgeType);
        Optional<KnowledgeDocument> duplicate = documentRepository
                .findFirstByChecksumAndStatusOrderByCreatedAtDesc(checksum, KnowledgeDocumentStatus.INDEXED);
        if (duplicate.isPresent()) {
            Files.deleteIfExists(Path.of(storedPath));
            return KnowledgeDocumentVO.from(duplicate.get());
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(UUID.randomUUID().toString());
        document.setTitle(hasText(requestedTitle) ? requestedTitle.strip() : fileName);
        document.setSourceType(sourceType);
        document.setSourceUri(sourceUri);
        document.setSourceId(sourceId);
        document.setStoredPath(storedPath);
        document.setMimeType(declaredMime);
        document.setChecksum(checksum);
        document.setCity(normalize(city));
        document.setKnowledgeType(normalizeType(knowledgeType));
        document.setStatus(KnowledgeDocumentStatus.PROCESSING);
        documentRepository.save(document);

        List<String> pointIds = new ArrayList<>();
        try {
            DocumentParserService.ParsedDocument parsed = parser.parse(bytes, fileName, declaredMime);
            document.setMimeType(parsed.mimeType());
            document.setCharacterCount(parsed.text() == null ? 0 : parsed.text().length());
            qualityService.requireIndexable(parsed);
            String text = cleaner.clean(parsed.text());
            if (text.length() < 50) throw new IllegalArgumentException("文档清洗后有效文本不足50字");
            if (!hasText(document.getCity())) document.setCity(detectSingleCity(text));
            DocumentChunkingService.StructuredChunks structured = chunker.chunkStructured(text);
            if (structured.childCount() == 0) throw new IllegalArgumentException("文档未生成有效分块");
            String title = hasText(requestedTitle) ? requestedTitle.strip()
                    : hasText(parsed.title()) ? parsed.title().strip() : fileName;
            List<PendingChild> pendingChildren = new ArrayList<>();
            List<KnowledgeParentChunk> parentRows = new ArrayList<>();
            for (DocumentChunkingService.ParentChunkDraft parent : structured.parents()) {
                String parentId = UUID.nameUUIDFromBytes((document.getId() + ":parent:" + parent.parentIndex())
                        .getBytes(StandardCharsets.UTF_8)).toString();
                KnowledgeParentChunk parentRow = new KnowledgeParentChunk();
                parentRow.setId(parentId);
                parentRow.setDocumentId(document.getId());
                parentRow.setParentIndex(parent.parentIndex());
                parentRow.setSectionPath(parent.sectionPath());
                parentRow.setContent(parent.content());
                parentRow.setCharacterCount(parent.content().length());
                parentRows.add(parentRow);
                for (DocumentChunkingService.ChildChunkDraft child : parent.children()) {
                    pendingChildren.add(new PendingChild(parentId, child.sectionPath(), child.content()));
                }
            }
            List<String> embeddingTexts = pendingChildren.stream()
                    .map(child -> title + (hasText(document.getCity()) ? "\n城市：" + document.getCity() : "")
                            + "\n" + child.content())
                    .toList();
            List<List<Float>> vectors = embeddingClient.embedBatch(embeddingTexts);
            if (vectors.size() != pendingChildren.size()) throw new IllegalStateException("Embedding数量与分块数量不一致");
            qdrantClient.ensureCollection(vectors.getFirst().size());

            List<QdrantClient.Point> points = new ArrayList<>();
            List<KnowledgeChunk> rows = new ArrayList<>();
            for (int index = 0; index < pendingChildren.size(); index++) {
                PendingChild pending = pendingChildren.get(index);
                String chunkId = UUID.randomUUID().toString();
                String pointId = UUID.nameUUIDFromBytes((document.getId() + ":" + index)
                        .getBytes(StandardCharsets.UTF_8)).toString();
                pointIds.add(pointId);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("id", chunkId);
                payload.put("entityId", chunkId);
                payload.put("documentId", document.getId());
                payload.put("chunkIndex", index);
                payload.put("parentChunkId", pending.parentChunkId());
                payload.put("sectionPath", pending.sectionPath());
                payload.put("chunkLevel", "CHILD");
                payload.put("name", title);
                payload.put("canonicalName", title);
                payload.put("type", "document_chunk");
                payload.put("knowledgeType", document.getKnowledgeType());
                payload.put("city", Objects.toString(document.getCity(), ""));
                payload.put("description", pending.content());
                payload.put("content", pending.content());
                payload.put("sourceUri", sourceUri);
                payload.put("sourceId", Objects.toString(sourceId, ""));
                payload.put("mimeType", Objects.toString(parsed.mimeType(), declaredMime));
                payload.put("tags", List.of(document.getKnowledgeType()));
                payload.putAll(embeddingClient.metadata());
                points.add(new QdrantClient.Point(pointId, vectors.get(index), payload));

                KnowledgeChunk row = new KnowledgeChunk();
                row.setId(chunkId);
                row.setDocumentId(document.getId());
                row.setChunkIndex(index);
                row.setParentChunkId(pending.parentChunkId());
                row.setSectionPath(pending.sectionPath());
                row.setContent(pending.content());
                row.setCharacterCount(pending.content().length());
                row.setQdrantPointId(pointId);
                rows.add(row);
            }
            qdrantClient.upsert(points);
            parentChunkRepository.saveAll(parentRows);
            chunkRepository.saveAll(rows);
            document.setTitle(title);
            document.setMimeType(parsed.mimeType());
            document.setCharacterCount(text.length());
            document.setChunkCount(pendingChildren.size());
            document.setStatus(KnowledgeDocumentStatus.INDEXED);
            document.setIndexedAt(LocalDateTime.now());
            documentRepository.save(document);
            bm25SearchService.invalidate();
            return KnowledgeDocumentVO.from(document);
        } catch (Exception error) {
            try { qdrantClient.deletePoints(pointIds); } catch (Exception ignored) {}
            try { chunkRepository.deleteAllByDocumentId(document.getId()); } catch (Exception ignored) {}
            try { parentChunkRepository.deleteAllByDocumentId(document.getId()); } catch (Exception ignored) {}
            document.setStatus(error instanceof DocumentQualityException quality
                    ? quality.status() : KnowledgeDocumentStatus.FAILED);
            document.setErrorMessage(abbreviate(error.getMessage()));
            documentRepository.save(document);
            throw error;
        }
    }

    private Path store(byte[] bytes, String fileName) throws Exception {
        Files.createDirectories(storageDir);
        String extension = extension(fileName);
        Path target = storageDir.resolve(UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension)).normalize();
        if (!target.startsWith(storageDir)) throw new SecurityException("非法存储路径");
        Files.write(target, bytes);
        return target;
    }

    private URI validatePublicUrl(String value) throws Exception {
        URI uri = URI.create(value == null ? "" : value.strip());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) throw new IllegalArgumentException("仅支持HTTP/HTTPS网页URL");
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("不允许加载本机或内网地址");
            }
        }
        return uri;
    }

    private void validateExtension(String fileName) {
        if (!SUPPORTED_EXTENSIONS.contains(extension(fileName))) {
            throw new IllegalArgumentException("仅支持 PDF、Word、HTML、Markdown 和 TXT 文件");
        }
    }

    private String safeFileName(String value) {
        String name = fileName(value, "document.txt").replaceAll("[\\r\\n]", "_");
        return name.length() > 240 ? name.substring(name.length() - 240) : name;
    }

    private String fileName(String path, String fallback) {
        if (path == null || path.isBlank()) return fallback;
        String normalized = path.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return name.isBlank() ? fallback : name;
    }

    private String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) { return hasText(value) ? value.strip() : null; }
    private String detectSingleCity(String text) {
        List<String> matches = KNOWN_CITIES.stream().filter(text::contains).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    private String normalizeType(String value) {
        String type = hasText(value) ? value.strip().toLowerCase(Locale.ROOT) : "guide";
        return Set.of("guide", "poi", "food", "transport", "culture", "notice", "exhibition",
                "opening", "ticket").contains(type) ? type : "guide";
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String abbreviate(String value) {
        String text = Objects.toString(value, "处理失败");
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
    private String sha256(byte[] bytes, String city, String type) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes);
        digest.update(Objects.toString(city, "").getBytes(StandardCharsets.UTF_8));
        digest.update(Objects.toString(type, "").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    public record UrlFetchResult(byte[] content, String resolvedUrl, String fileName, String mimeType,
                                 String etag, String lastModified, boolean notModified) {}

    private record PendingChild(String parentChunkId, String sectionPath, String content) {}
}
