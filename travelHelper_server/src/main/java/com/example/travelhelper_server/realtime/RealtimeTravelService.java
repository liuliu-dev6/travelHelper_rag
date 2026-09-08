package com.example.travelhelper_server.realtime;

import com.example.travelhelper_server.intent.QueryIntent;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class RealtimeTravelService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeTravelService.class);

    private final List<RealtimeTravelProvider> providers;

    public RealtimeTravelService(List<RealtimeTravelProvider> providers) {
        this.providers = providers;
    }

    public String toolName(QueryIntent intent, String query) {
        return providers.stream().filter(provider -> provider.supports(intent, query))
                .map(RealtimeTravelProvider::toolName).findFirst().orElse("realtime_lookup");
    }

    public RealtimeTravelProvider.RealtimeResult query(QueryIntent intent, String query, String city) {
        RealtimeTravelProvider.RealtimeResult firstUnavailable = null;
        for (RealtimeTravelProvider provider : providers) {
            if (!provider.supports(intent, query)) continue;
            try {
                RealtimeTravelProvider.RealtimeResult result = provider.query(intent, query, city);
                if (result != null && result.available()) return result;
                if (firstUnavailable == null && result != null) firstUnavailable = result;
            } catch (Exception error) {
                log.warn("实时工具 {} 调用失败: {}", provider.toolName(), error.getMessage());
            }
        }
        if (firstUnavailable != null) return firstUnavailable;
        return new RealtimeTravelProvider.RealtimeResult(false,
                "天气和空气质量服务暂时不可用，我不会猜测实时数据，请稍后重试。",
                null, null);
    }
}
