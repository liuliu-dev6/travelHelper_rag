package com.example.travelhelper_server.config;

import com.example.travelhelper_server.vector.QdrantClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时只读展示Embedding与Qdrant配置，方便确认模型、维度和索引状态。
 * 不发起Embedding请求，不输出API Key，也不会阻断应用启动。
 */
@Component
@Order(30)
@RequiredArgsConstructor
public class EmbeddingConfigurationReporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfigurationReporter.class);

    private final QdrantClient qdrantClient;

    @Value("${llm.embedding-provider:unknown}")
    private String provider;
    @Value("${llm.embedding-model:unknown}")
    private String endpoint;
    @Value("${llm.embedding-expected-model:unknown}")
    private String expectedModel;
    @Value("${llm.embedding-expected-dimensions:0}")
    private int expectedDimensions;
    @Value("${llm.embedding-dimensions:2048}")
    private int requestedDimensions;
    @Value("${llm.embedding-api:text}")
    private String api;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[embedding-config] provider={}, endpoint={}, expectedModel={}, requestedDimensions={}, expectedDimensions={}, api={}",
                provider, endpoint, expectedModel, requestedDimensions, expectedDimensions, api);
        try {
            QdrantClient.CollectionInfo info = qdrantClient.collectionInfo();
            log.info("[qdrant-config] collection={}, actualDimensions={}, distance={}, points={}, status={}",
                    info.collection(), info.dimensions(), info.distance(), info.pointsCount(), info.status());
            try {
                log.info("[qdrant-health] checkedVectors={}, status=valid", qdrantClient.verifyVectorSample());
            } catch (Exception invalid) {
                log.error("[qdrant-health] 库存向量校验失败，检索指标不可用，请诊断后重建：{}", invalid.getMessage());
            }
            if (expectedDimensions > 0 && info.dimensions() > 0 && expectedDimensions != info.dimensions()) {
                log.warn("[embedding-config] expectedDimensions={} 与Qdrant actualDimensions={}不一致；"
                                + "本消息仅用于诊断，未改变应用启动结果",
                        expectedDimensions, info.dimensions());
            }
            if (requestedDimensions != info.dimensions()) {
                log.warn("[embedding-config] requestedDimensions={} 与Qdrant actualDimensions={}不一致；"
                                + "请使用匹配维度的独立Collection，当前仅告警且不改变启动结果",
                        requestedDimensions, info.dimensions());
            }
        } catch (Exception error) {
            log.warn("[qdrant-config] 暂时无法读取Collection配置；不影响应用启动：{}", error.getMessage());
        }
    }
}
