package com.example.travelhelper_server.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Open-Meteo HTTP 客户端：只负责可追溯的地理编码、天气和空气质量数据读取。 */
@Component
public class OpenMeteoClient {
    /** Open-Meteo 对部分中文城市名会优先返回同名乡镇，常用旅游城市改用稳定英文检索名消歧。 */
    private static final Map<String, String> CITY_SEARCH_NAMES = citySearchNames();
    private final boolean enabled;
    private final String geocodingUrl;
    private final String forecastUrl;
    private final String airQualityUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenMeteoClient(
            @Value("${realtime.open-meteo.enabled:true}") boolean enabled,
            @Value("${realtime.open-meteo.geocoding-url:https://geocoding-api.open-meteo.com/v1/search}") String geocodingUrl,
            @Value("${realtime.open-meteo.forecast-url:https://api.open-meteo.com/v1/forecast}") String forecastUrl,
            @Value("${realtime.open-meteo.air-quality-url:https://air-quality-api.open-meteo.com/v1/air-quality}") String airQualityUrl,
            @Value("${realtime.open-meteo.timeout-ms:10000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.geocodingUrl = geocodingUrl;
        this.forecastUrl = forecastUrl;
        this.airQualityUrl = airQualityUrl;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .readTimeout(Duration.ofMillis(timeoutMs))
                .callTimeout(Duration.ofMillis(timeoutMs + 2000))
                .build();
    }

    public boolean enabled() {
        return enabled;
    }

    public Location geocode(String city) throws Exception {
        if (!enabled) return null;
        String searchName = CITY_SEARCH_NAMES.entrySet().stream()
                .filter(entry -> city.contains(entry.getKey()))
                .max(Map.Entry.comparingByKey(java.util.Comparator.comparingInt(String::length)))
                .map(Map.Entry::getValue).orElse(city);
        HttpUrl url = HttpUrl.get(geocodingUrl).newBuilder()
                .addQueryParameter("name", searchName)
                .addQueryParameter("count", "10")
                .addQueryParameter("language", "zh")
                .addQueryParameter("countryCode", "CN")
                .build();
        JsonNode results = get(url).path("results");
        if (!results.isArray() || results.isEmpty()) return null;
        JsonNode best = results.get(0);
        return new Location(best.path("name").asText(city), best.path("admin1").asText(""),
                best.path("country").asText("中国"), best.path("latitude").asDouble(),
                best.path("longitude").asDouble(), best.path("timezone").asText("Asia/Shanghai"));
    }

    public JsonNode weather(Location location) throws Exception {
        HttpUrl url = HttpUrl.get(forecastUrl).newBuilder()
                .addQueryParameter("latitude", String.valueOf(location.latitude()))
                .addQueryParameter("longitude", String.valueOf(location.longitude()))
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("forecast_days", "7")
                .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m")
                .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,uv_index_max,sunrise,sunset")
                .build();
        return get(url);
    }

    public JsonNode airQuality(Location location) throws Exception {
        HttpUrl url = HttpUrl.get(airQualityUrl).newBuilder()
                .addQueryParameter("latitude", String.valueOf(location.latitude()))
                .addQueryParameter("longitude", String.valueOf(location.longitude()))
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("current", "us_aqi,pm2_5,pm10,uv_index")
                .build();
        return get(url);
    }

    private JsonNode get(HttpUrl url) throws Exception {
        Request request = new Request.Builder().url(url)
                .header("User-Agent", "travelHelper/1.0")
                .get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Open-Meteo HTTP " + response.code());
            }
            JsonNode json = objectMapper.readTree(body);
            if (json.path("error").asBoolean(false)) {
                throw new IllegalStateException(json.path("reason").asText("Open-Meteo 返回错误"));
            }
            return json;
        }
    }

    private static Map<String, String> citySearchNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("呼和浩特", "Hohhot"); names.put("乌鲁木齐", "Urumqi");
        names.put("哈尔滨", "Harbin"); names.put("石家庄", "Shijiazhuang");
        names.put("北京", "Beijing"); names.put("上海", "Shanghai");
        names.put("天津", "Tianjin"); names.put("重庆", "Chongqing");
        names.put("广州", "Guangzhou"); names.put("深圳", "Shenzhen");
        names.put("珠海", "Zhuhai"); names.put("佛山", "Foshan"); names.put("东莞", "Dongguan");
        names.put("长沙", "Changsha"); names.put("武汉", "Wuhan");
        names.put("南京", "Nanjing"); names.put("苏州", "Suzhou"); names.put("无锡", "Wuxi");
        names.put("杭州", "Hangzhou"); names.put("宁波", "Ningbo");
        names.put("温州", "Wenzhou"); names.put("绍兴", "Shaoxing");
        names.put("成都", "Chengdu"); names.put("西安", "Xi'an");
        names.put("昆明", "Kunming"); names.put("大理", "Dali"); names.put("丽江", "Lijiang");
        names.put("贵阳", "Guiyang"); names.put("南宁", "Nanning"); names.put("桂林", "Guilin");
        names.put("海口", "Haikou"); names.put("三亚", "Sanya");
        names.put("福州", "Fuzhou"); names.put("厦门", "Xiamen"); names.put("泉州", "Quanzhou");
        names.put("南昌", "Nanchang"); names.put("合肥", "Hefei");
        names.put("济南", "Jinan"); names.put("青岛", "Qingdao"); names.put("烟台", "Yantai");
        names.put("郑州", "Zhengzhou"); names.put("洛阳", "Luoyang"); names.put("太原", "Taiyuan");
        names.put("沈阳", "Shenyang"); names.put("大连", "Dalian"); names.put("长春", "Changchun");
        names.put("兰州", "Lanzhou"); names.put("西宁", "Xining");
        names.put("银川", "Yinchuan"); names.put("拉萨", "Lhasa"); names.put("张家界", "Zhangjiajie");
        return Map.copyOf(names);
    }

    public record Location(String name, String admin1, String country,
                           double latitude, double longitude, String timezone) {
        public String displayName() {
            return admin1 == null || admin1.isBlank() || name.contains(admin1) ? name : admin1 + " " + name;
        }
    }
}
