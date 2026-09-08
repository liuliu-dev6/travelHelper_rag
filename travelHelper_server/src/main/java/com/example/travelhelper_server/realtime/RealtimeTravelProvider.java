package com.example.travelhelper_server.realtime;

import com.example.travelhelper_server.intent.QueryIntent;

/** 天气和空气质量供应商扩展点；实现类必须返回可追溯的数据源和查询时间。 */
public interface RealtimeTravelProvider {
    boolean supports(QueryIntent intent, String query);

    String toolName();

    RealtimeResult query(QueryIntent intent, String query, String city) throws Exception;

    record RealtimeResult(boolean available, String content, String source, String observedAt) {
    }
}
