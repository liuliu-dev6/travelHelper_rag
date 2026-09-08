package com.example.travelhelper_server.subscription;

import com.example.travelhelper_server.entity.KnowledgeSource;
import com.example.travelhelper_server.repository.KnowledgeSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Order(20)
@ConditionalOnProperty(name = "knowledge.sources.bootstrap-changsha", havingValue = "true", matchIfMissing = true)
public class ChangshaKnowledgeSourceInitializer implements ApplicationRunner {
    private final KnowledgeSourceRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<Definition> sources = List.of(
                new Definition("changsha-culture-notices", "长沙市文旅广电局通知公告",
                        "http://wlgd.changsha.gov.cn/zwgk/tzgg/", null, "notice",
                        "闭园 开放 恢复 预约 景区 活动", 5, KnowledgeRefreshPolicy.EVERY_2_HOURS, 100),
                new Definition("hunan-culture-travel", "湖南省文化和旅游厅景区动态",
                        "http://whhlyt.hunan.gov.cn/whhlyt/xxgk2019/xxgkml/tzgg/", null, "notice",
                        "闭园 开放 恢复 预约 景区 出游", 5, KnowledgeRefreshPolicy.EVERY_2_HOURS, 95),
                new Definition("yuelu-orange-isle", "岳麓山—橘子洲旅游区权威动态",
                        "https://hnxjxq.rednet.cn/", "岳麓山 橘子洲", "opening",
                        "岳麓山 橘子洲 开放 闭园 预约 观光车 索道", 8,
                        KnowledgeRefreshPolicy.EVERY_2_HOURS, 90),
                new Definition("hunan-museum", "湖南博物院开放与展览",
                        "https://www.hnmuseum.com/", "湖南博物院", "exhibition",
                        "展览 陈列 开放 闭馆 预约 公告", 6, KnowledgeRefreshPolicy.DAILY, 88),
                new Definition("changsha-zoo", "长沙生态动物园",
                        "http://www.cszoo.com.cn/", "长沙生态动物园", "opening",
                        "公告 活动 开放 闭园 营业 票价", 5, KnowledgeRefreshPolicy.DAILY, 80),
                new Definition("changsha-sea-world", "长沙海底世界",
                        "http://www.cshdsj.com.cn/", "长沙海底世界", "opening",
                        "公告 活动 开放 闭园 营业 演出 票价", 5, KnowledgeRefreshPolicy.DAILY, 78),
                new Definition("changsha-metro", "长沙地铁运营服务",
                        "https://www.hncsmtr.com/905/", "长沙地铁", "transport",
                        "运营 调整 延长 停运 公告 服务时间", 6,
                        KnowledgeRefreshPolicy.EVERY_2_HOURS, 85)
        );
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < sources.size(); index++) {
            Definition definition = sources.get(index);
            LocalDateTime initialRefreshAt = now.plusMinutes(index * 2L);
            KnowledgeSource source = repository.findByCode(definition.code()).orElseGet(() -> {
                KnowledgeSource created = new KnowledgeSource();
                created.setId(UUID.randomUUID().toString());
                created.setCode(definition.code());
                created.setRefreshPolicy(definition.policy());
                created.setEnabled(true);
                created.setStatus(KnowledgeSourceStatus.PENDING);
                created.setNextRefreshAt(initialRefreshAt);
                return created;
            });
            boolean crawlConfigChanged = !definition.url().equals(source.getUrl())
                    || !definition.crawlKeywords().equals(source.getCrawlKeywords())
                    || definition.maxLinkedPages() != source.getMaxLinkedPages();
            source.setName(definition.name());
            source.setUrl(definition.url());
            source.setCity("长沙");
            source.setEntityName(definition.entityName());
            source.setKnowledgeType(definition.knowledgeType());
            source.setCrawlKeywords(definition.crawlKeywords());
            source.setMaxLinkedPages(definition.maxLinkedPages());
            source.setPriority(definition.priority());
            // 内置来源地址升级时清空HTTP/正文指纹，强制重新校验；保留管理员设置的启停和刷新策略。
            if (crawlConfigChanged) {
                source.setEtag(null);
                source.setLastModified(null);
                source.setLastChecksum(null);
                source.setStatus(source.isEnabled() ? KnowledgeSourceStatus.PENDING : KnowledgeSourceStatus.DISABLED);
                source.setNextRefreshAt(source.isEnabled() && source.getRefreshPolicy() != KnowledgeRefreshPolicy.MANUAL
                        ? initialRefreshAt : null);
            }
            repository.save(source);
        }
    }

    private record Definition(String code, String name, String url, String entityName,
                              String knowledgeType, String crawlKeywords, int maxLinkedPages,
                              KnowledgeRefreshPolicy policy, int priority) {}
}
