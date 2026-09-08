package com.example.travelhelper_server.subscription;

import com.example.travelhelper_server.entity.KnowledgeSource;
import com.example.travelhelper_server.ingestion.KnowledgeIngestionService;
import com.example.travelhelper_server.repository.KnowledgeSourceRepository;
import com.example.travelhelper_server.vo.KnowledgeDocumentVO;
import com.example.travelhelper_server.vo.KnowledgeRefreshResultVO;
import com.example.travelhelper_server.vo.KnowledgeSourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class KnowledgeSourceService {
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeIngestionService ingestionService;
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

    public List<KnowledgeSourceVO> list() {
        return sourceRepository.findAllByOrderByPriorityDescNameAsc().stream()
                .map(KnowledgeSourceVO::from).toList();
    }

    public KnowledgeSourceVO update(String id, boolean enabled, KnowledgeRefreshPolicy policy) {
        KnowledgeSource source = get(id);
        boolean policyChanged = source.getRefreshPolicy() != policy;
        source.setEnabled(enabled);
        source.setRefreshPolicy(policy);
        if (!enabled) {
            source.setStatus(KnowledgeSourceStatus.DISABLED);
            source.setNextRefreshAt(null);
        } else {
            if (source.getStatus() == KnowledgeSourceStatus.DISABLED) source.setStatus(KnowledgeSourceStatus.PENDING);
            if (policy == KnowledgeRefreshPolicy.MANUAL) source.setNextRefreshAt(null);
            else if (policyChanged || source.getNextRefreshAt() == null) source.setNextRefreshAt(LocalDateTime.now());
        }
        return KnowledgeSourceVO.from(sourceRepository.save(source));
    }

    public KnowledgeRefreshResultVO refresh(String id) {
        KnowledgeSource source = get(id);
        if (!source.isEnabled()) throw new IllegalStateException("知识源已停用，请先启用");
        if (!refreshing.add(id)) throw new IllegalStateException("该知识源正在同步，请稍后刷新");
        try {
            return doRefresh(source);
        } finally {
            refreshing.remove(id);
        }
    }

    public void refreshDueSources() {
        sourceRepository.findTop2ByEnabledTrueAndNextRefreshAtLessThanEqualOrderByPriorityDescNextRefreshAtAsc(
                LocalDateTime.now()).forEach(source -> {
            try { refresh(source.getId()); }
            catch (Exception ignored) { /* 失败状态和原因已在doRefresh中持久化。 */ }
        });
    }

    private KnowledgeRefreshResultVO doRefresh(KnowledgeSource source) {
        LocalDateTime now = LocalDateTime.now();
        source.setStatus(KnowledgeSourceStatus.SYNCING);
        source.setLastCheckedAt(now);
        source.setLastError(null);
        sourceRepository.save(source);
        try {
            KnowledgeIngestionService.UrlFetchResult fetched = ingestionService.fetchUrlBundle(
                    source.getUrl(), source.getEtag(), source.getLastModified(),
                    source.getCrawlKeywords(), source.getMaxLinkedPages());
            source.setEtag(firstNonBlank(fetched.etag(), source.getEtag()));
            source.setLastModified(firstNonBlank(fetched.lastModified(), source.getLastModified()));

            if (fetched.notModified()) {
                markSuccess(source, KnowledgeSourceStatus.UNCHANGED, now);
                return new KnowledgeRefreshResultVO("NOT_MODIFIED", false, KnowledgeSourceVO.from(source));
            }

            String checksum = ingestionService.normalizedContentChecksum(fetched);
            if (Objects.equals(checksum, source.getLastChecksum())) {
                markSuccess(source, KnowledgeSourceStatus.UNCHANGED, now);
                return new KnowledgeRefreshResultVO("CHECKSUM_UNCHANGED", false, KnowledgeSourceVO.from(source));
            }

            KnowledgeDocumentVO newDocument = ingestionService.ingestFetchedUrl(fetched, source.getUrl(),
                    source.getName(), source.getCity(), source.getKnowledgeType(), source.getId());
            String previousDocumentId = source.getCurrentDocumentId();
            source.setCurrentDocumentId(newDocument.id());
            source.setLastChecksum(checksum);
            markSuccess(source, KnowledgeSourceStatus.ACTIVE, now);

            if (previousDocumentId != null && !previousDocumentId.equals(newDocument.id())) {
                try {
                    ingestionService.deleteSubscriptionVersion(previousDocumentId);
                } catch (Exception cleanupError) {
                    source.setLastError("新版本已生效，但旧版本清理失败：" + abbreviate(cleanupError.getMessage()));
                    sourceRepository.save(source);
                }
            }
            return new KnowledgeRefreshResultVO("UPDATED", true, KnowledgeSourceVO.from(source));
        } catch (Exception error) {
            source.setStatus(KnowledgeSourceStatus.FAILED);
            source.setLastError(abbreviate(error.getMessage()));
            source.setNextRefreshAt(nextRefresh(source.getRefreshPolicy(), now));
            sourceRepository.save(source);
            throw new IllegalStateException("知识源同步失败：" + source.getLastError(), error);
        }
    }

    private void markSuccess(KnowledgeSource source, KnowledgeSourceStatus status, LocalDateTime now) {
        source.setStatus(status);
        source.setLastSuccessAt(now);
        source.setNextRefreshAt(nextRefresh(source.getRefreshPolicy(), now));
        sourceRepository.save(source);
    }

    private LocalDateTime nextRefresh(KnowledgeRefreshPolicy policy, LocalDateTime from) {
        return policy.interval() == null ? null : from.plus(policy.interval());
    }

    private KnowledgeSource get(String id) {
        return sourceRepository.findById(id).orElseThrow(() -> new NoSuchElementException("知识源不存在"));
    }

    private String firstNonBlank(String candidate, String fallback) {
        return candidate == null || candidate.isBlank() ? fallback : candidate;
    }

    private String abbreviate(String value) {
        String text = value == null || value.isBlank() ? "未知错误" : value;
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
