package com.example.travelhelper_server.realtime;

import com.example.travelhelper_server.intent.QueryIntent;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(20)
@RequiredArgsConstructor
public class OpenMeteoWeatherProvider implements RealtimeTravelProvider {
    private static final Pattern WEATHER = Pattern.compile(
            "天气|气温|温度|下雨|降雨|降水|冷不冷|热不热|穿什么|带伞|紫外线|风力|刮风");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})月(\\d{1,2})日?");
    private final OpenMeteoClient client;

    @Override
    public boolean supports(QueryIntent intent, String query) {
        return client.enabled() && !query.matches(".*(空气质量|AQI|PM2\\.?5|雾霾).*" )
                && WEATHER.matcher(query).find();
    }

    @Override
    public String toolName() {
        return "get_weather_forecast";
    }

    @Override
    public RealtimeResult query(QueryIntent intent, String query, String city) throws Exception {
        if (city == null || city.isBlank()) {
            return unavailable("请告诉我需要查询天气的旅游城市，例如“明天长沙天气怎么样”。");
        }
        OpenMeteoClient.Location location = client.geocode(city);
        if (location == null) return unavailable("没有找到“" + city + "”对应的城市，请检查城市名称。");
        JsonNode weather = client.weather(location);
        JsonNode daily = weather.path("daily");
        List<Integer> indexes = selectDays(query, intent.slots().dateText(), daily.path("time"));
        if (indexes.isEmpty()) {
            return unavailable("当前天气工具只能查询未来7天，指定日期不在可用预报范围内。");
        }

        StringBuilder content = new StringBuilder(location.displayName()).append("天气：\n");
        boolean currentRequested = isCurrent(query, intent.slots().dateText()) && indexes.getFirst() == 0;
        if (currentRequested && weather.path("current").isObject()) {
            JsonNode current = weather.path("current");
            content.append("当前：").append(weatherText(current.path("weather_code").asInt(-1)))
                    .append("，").append(decimal(current.path("temperature_2m"))).append("℃")
                    .append("，体感").append(decimal(current.path("apparent_temperature"))).append("℃")
                    .append("，湿度").append(integer(current.path("relative_humidity_2m"))).append("%")
                    .append("，风速").append(decimal(current.path("wind_speed_10m"))).append("km/h。\n");
        }
        for (int index : indexes) appendDay(content, daily, index);
        appendTravelAdvice(content, daily, indexes);
        return new RealtimeResult(true, content.toString().strip(),
                "Open-Meteo Weather Forecast API", observedAt(weather));
    }

    private List<Integer> selectDays(String query, String dateText, JsonNode times) {
        List<LocalDate> dates = new ArrayList<>();
        if (times.isArray()) times.forEach(value -> dates.add(LocalDate.parse(value.asText())));
        if (dates.isEmpty()) return List.of();
        String dateQuery = (dateText == null ? "" : dateText) + " " + query;
        if (dateQuery.contains("周末")) {
            List<Integer> result = new ArrayList<>();
            for (int index = 0; index < dates.size(); index++) {
                DayOfWeek day = dates.get(index).getDayOfWeek();
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) result.add(index);
            }
            return result;
        }
        if (dateQuery.matches(".*(未来|接下来).*(几天|三天|3天).*")) {
            return java.util.stream.IntStream.range(0, Math.min(3, dates.size())).boxed().toList();
        }
        if (dateQuery.matches(".*(未来一周|未来7天|未来七天|这一周|本周).*")) {
            return java.util.stream.IntStream.range(0, dates.size()).boxed().toList();
        }
        int relative = dateQuery.contains("后天") ? 2 : dateQuery.contains("明天") ? 1 : 0;
        Matcher matcher = MONTH_DAY.matcher(dateQuery);
        if (matcher.find()) {
            LocalDate now = dates.getFirst();
            LocalDate target;
            try {
                target = LocalDate.of(now.getYear(), Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)));
                if (target.isBefore(now)) target = target.plusYears(1);
            } catch (Exception ignored) {
                return List.of();
            }
            int index = dates.indexOf(target);
            return index < 0 ? List.of() : List.of(index);
        }
        return relative < dates.size() ? List.of(relative) : List.of();
    }

    private void appendDay(StringBuilder content, JsonNode daily, int index) {
        String date = daily.path("time").path(index).asText();
        content.append(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)))
                .append("：").append(weatherText(intAt(daily, "weather_code", index)))
                .append("，").append(decimalAt(daily, "temperature_2m_min", index)).append("～")
                .append(decimalAt(daily, "temperature_2m_max", index)).append("℃")
                .append("，最高降水概率").append(integerAt(daily, "precipitation_probability_max", index)).append("%")
                .append("，预计降水").append(decimalAt(daily, "precipitation_sum", index)).append("mm")
                .append("，最大风速").append(decimalAt(daily, "wind_speed_10m_max", index)).append("km/h。\n");
    }

    private void appendTravelAdvice(StringBuilder content, JsonNode daily, List<Integer> indexes) {
        double maxRain = indexes.stream().mapToDouble(index -> doubleAt(daily, "precipitation_probability_max", index)).max().orElse(0);
        double maxUv = indexes.stream().mapToDouble(index -> doubleAt(daily, "uv_index_max", index)).max().orElse(0);
        double maxTemperature = indexes.stream().mapToDouble(index -> doubleAt(daily, "temperature_2m_max", index)).max().orElse(0);
        double minTemperature = indexes.stream().mapToDouble(index -> doubleAt(daily, "temperature_2m_min", index)).min().orElse(0);
        List<String> advice = new ArrayList<>();
        if (maxRain >= 50) advice.add("降雨概率较高，建议携带雨具并准备室内备选行程");
        if (maxUv >= 6) advice.add("紫外线较强，建议做好防晒");
        if (maxTemperature >= 35) advice.add("最高气温较高，避免午后长时间户外活动并及时补水");
        if (minTemperature <= 5) advice.add("最低气温较低，早晚注意保暖");
        if (!advice.isEmpty()) content.append("出行提示：").append(String.join("；", advice)).append("。");
    }

    private boolean isCurrent(String query, String dateText) {
        String value = (dateText == null ? "" : dateText) + query;
        return !(value.contains("明天") || value.contains("后天") || value.contains("周末")
                || MONTH_DAY.matcher(value).find() || value.contains("未来"));
    }

    private String observedAt(JsonNode weather) {
        String time = weather.path("current").path("time").asText("");
        String timezone = weather.path("timezone_abbreviation").asText(weather.path("timezone").asText("当地时间"));
        return time.isBlank() ? "实时查询" : time + " " + timezone;
    }

    private RealtimeResult unavailable(String message) {
        return new RealtimeResult(false, message, null, null);
    }

    private String weatherText(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "大致晴朗";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "有雾";
            case 51, 53, 55, 56, 57 -> "毛毛雨";
            case 61, 63, 65, 66, 67 -> "有雨";
            case 71, 73, 75, 77 -> "有雪";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95, 96, 99 -> "雷雨";
            default -> "天气状况未知";
        };
    }

    private int intAt(JsonNode node, String field, int index) {
        return node.path(field).path(index).asInt(-1);
    }

    private double doubleAt(JsonNode node, String field, int index) {
        return node.path(field).path(index).asDouble(0);
    }

    private String decimalAt(JsonNode node, String field, int index) {
        return decimal(node.path(field).path(index));
    }

    private String integerAt(JsonNode node, String field, int index) {
        return integer(node.path(field).path(index));
    }

    private String decimal(JsonNode value) {
        return String.format(Locale.ROOT, "%.1f", value.asDouble(0));
    }

    private String integer(JsonNode value) {
        return String.valueOf(Math.round(value.asDouble(0)));
    }
}
