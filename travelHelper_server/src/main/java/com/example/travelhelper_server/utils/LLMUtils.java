package com.example.travelhelper_server.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


@Component
public class LLMUtils {

    private String apikey;
    private String baseURL;
    private String model;
    private OkHttpClient client;
    private ObjectMapper objectMapper=new ObjectMapper();

    public LLMUtils(@Value("${llm.api-key}") String apikey,
                    @Value("${llm.baseURL}") String baseURL,
                    @Value("${llm.model}") String model) {
        this.apikey = apikey;
        this.baseURL = baseURL;
        this.model = model;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    // 旅游推荐接口
    public String chat(String systemPrompt, String userPrompt) throws IOException {
        return chat(systemPrompt, userPrompt, 0.7, 0);
    }

    /** 非流式调用；可为分类等短任务单独设置温度和总超时。 */
    public String chat(String systemPrompt, String userPrompt, double temperature, long timeoutMs) throws IOException {
        return chat(systemPrompt, userPrompt, temperature, timeoutMs, 1);
    }

    /**
     * 非流式调用的有限网络重试。真正的 callTimeout 不重试，避免一次提交占用双倍超时时间；
     * 仅对连接重置、HTTP/2 流重置等通常可瞬时恢复的网络错误重试。
     */
    public String chat(String systemPrompt, String userPrompt, double temperature,
                       long timeoutMs, int maxAttempts) throws IOException {
        int attempts = Math.max(1, Math.min(maxAttempts, 2));
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return executeChat(systemPrompt, userPrompt, temperature, timeoutMs);
            } catch (IOException error) {
                last = error;
                if (attempt >= attempts || !isTransientNetworkReset(error)) throw friendly(error, timeoutMs);
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("LLM请求重试被中断");
                }
            }
        }
        throw friendly(last, timeoutMs);
    }

    private String executeChat(String systemPrompt, String userPrompt,
                               double temperature, long timeoutMs) throws IOException {
        // 构建请求体
        String requestBody = buildRequestBody(systemPrompt, userPrompt, false, temperature);

        // 创建POST请求
        Request request = new Request.Builder()
                .url(baseURL + "/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apikey)
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        // 执行请求
        OkHttpClient requestClient = timeoutMs > 0
                ? client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
                : client;
        try (Response response = requestClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() == null ? "" : response.body().string();
                throw new IOException("LLM调用异常：" + response.code()
                        + (body.isBlank() ? "" : " " + abbreviate(body, 500)));
            }
            if (response.body() == null) throw new IOException("LLM调用异常：响应体为空");
            String responseBody = response.body().string();
            return extractContent(responseBody);
        }
    }

    static boolean isTransientNetworkReset(IOException error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException) return false;
        }
        String message = messages(error).toLowerCase();
        return message.contains("stream was reset") || message.contains("connection reset")
                || message.contains("unexpected end of stream") || message.contains("refused stream");
    }

    private IOException friendly(IOException error, long timeoutMs) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException || containsIgnoreCase(current.getMessage(), "timeout")) {
                long seconds = Math.max(1, timeoutMs / 1000);
                return new IOException("LLM请求超过" + seconds + "秒，请稍后重试或缩短文本", error);
            }
        }
        return error;
    }

    private static String messages(Throwable error) {
        StringBuilder result = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null) result.append(' ').append(current.getMessage());
        }
        return result.toString();
    }

    private static boolean containsIgnoreCase(String value, String part) {
        return value != null && value.toLowerCase().contains(part.toLowerCase());
    }

    private static String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    //模型返回数据处理
    private String extractContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText();
        }
        return "";
    }

    public String buildRequestBody(String systemPrompt, String userPrompt, Boolean stream) {
        return buildRequestBody(systemPrompt, userPrompt, stream, 0.7);
    }

    private String buildRequestBody(String systemPrompt, String userPrompt, Boolean stream, double temperature) {
        StringBuilder str = new StringBuilder();
        str.append("{");
        str.append("\"model\":\"" + model + "\",");
        str.append("\"stream\":" + stream + ",");
        str.append("\"messages\":[");

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            str.append("{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},");
        }


        str.append("{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}");
        str.append("],");
        str.append("\"temperature\":" + temperature);
        str.append("}");

        return str.toString();
    }
    // JSON字符串转义方法
    private String escapeJson(String text) {
        
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")      // 转义反斜杠
                .replace("\"", "\\\"")      // 转义双引号
                .replace("\n", "\\n")       // 转义换行符
                .replace("\r", "\\r")       // 转义回车符
                .replace("\t", "\\t");      // 转义制表符
    }
    //流式接口调用处理函数
    public String streamChat(String systemPrompt, String userPrompt, Consumer<String> callback) throws IOException {
        String requestBody = buildRequestBody(systemPrompt, userPrompt, true);
        Request request = new Request.Builder()
                .url(baseURL + "/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apikey)
                .addHeader("Accept", "text/event-stream")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();
        //记录完整的内容
        StringBuilder fullContent = new StringBuilder();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LLM调用异常： " + response.code());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if(line.startsWith("data: ")){
                        //截取字符串
                        String data = line.substring(6);
                        if(data.equals("[DONE]")){
                            break;
                        }
                        String content = parseStreamContent(data);
                        if(content != null&& !content.isEmpty()){
                            fullContent.append(content);
                            if(callback != null){
                                callback.accept(content);
                            }

                        }
                    }
                }
            }
            return fullContent.toString();
        }
    }

    // 大模型返回对话流数据处理函数
    private String parseStreamContent(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                return delta.path("content").asText("");
            }
        } catch (Exception e) {
            System.out.println("解析流式数据失败: {}" + e.getMessage());
        }
        return null;
    }
}
