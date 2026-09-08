package com.example.travelhelper_server.realtime;

import com.example.travelhelper_server.intent.QueryIntent;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Order(10)
@RequiredArgsConstructor
public class OpenMeteoAirQualityProvider implements RealtimeTravelProvider {
    private static final Pattern AIR_QUALITY = Pattern.compile(
            "空气质量|空气怎么样|雾霾|AQI|PM2\\.?5|PM10", Pattern.CASE_INSENSITIVE);
    private final OpenMeteoClient client;

    @Override
    public boolean supports(QueryIntent intent, String query) {
        return client.enabled() && AIR_QUALITY.matcher(query).find();
    }

    @Override
    public String toolName() {
        return "get_air_quality";
    }

    @Override
    public RealtimeResult query(QueryIntent intent, String query, String city) throws Exception {
        if (city == null || city.isBlank()) {
            return unavailable("请告诉我需要查询空气质量的旅游城市，例如“长沙现在空气质量怎么样”。");
        }
        OpenMeteoClient.Location location = client.geocode(city);
        if (location == null) return unavailable("没有找到“" + city + "”对应的城市，请检查城市名称。");
        JsonNode response = client.airQuality(location);
        JsonNode current = response.path("current");
        if (!current.isObject() || current.path("us_aqi").isMissingNode()) {
            return unavailable("暂时没有获取到“" + location.displayName() + "”的空气质量数据。");
        }
        int aqi = (int) Math.round(current.path("us_aqi").asDouble());
        double pm25 = current.path("pm2_5").asDouble();
        double pm10 = current.path("pm10").asDouble();
        double uv = current.path("uv_index").asDouble();
        StringBuilder content = new StringBuilder(location.displayName()).append("当前空气质量：")
                .append("AQI ").append(aqi).append("（").append(aqiLevel(aqi)).append("）")
                .append("，PM2.5 ").append(decimal(pm25)).append("μg/m³")
                .append("，PM10 ").append(decimal(pm10)).append("μg/m³")
                .append("，紫外线指数 ").append(decimal(uv)).append("。");
        if (aqi > 150) content.append("空气污染较明显，建议减少长时间户外活动并优先安排室内景点。");
        else if (aqi > 100) content.append("敏感人群建议减少长时间或高强度户外活动。");
        return new RealtimeResult(true, content.toString(),
                "Open-Meteo Air Quality API（CAMS 数据）", observedAt(response));
    }

    private String aqiLevel(int aqi) {
        if (aqi <= 50) return "良好";
        if (aqi <= 100) return "中等";
        if (aqi <= 150) return "对敏感人群不健康";
        if (aqi <= 200) return "不健康";
        if (aqi <= 300) return "非常不健康";
        return "危险";
    }

    private String observedAt(JsonNode response) {
        String time = response.path("current").path("time").asText("");
        String timezone = response.path("timezone_abbreviation").asText(response.path("timezone").asText("当地时间"));
        return time.isBlank() ? "实时查询" : time + " " + timezone;
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private RealtimeResult unavailable(String message) {
        return new RealtimeResult(false, message, null, null);
    }
}
