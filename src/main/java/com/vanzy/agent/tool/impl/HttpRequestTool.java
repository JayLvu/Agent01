package com.vanzy.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 请求工具(http_request): 让 LLM 可以调用任意外部 REST API。
 *
 * 支持 GET/POST/PUT/DELETE,可自定义 headers 与 JSON body。
 * 默认超时 15s,响应体截断到 20KB 防止上下文膨胀。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class HttpRequestTool implements Tool {

    private static final int MAX_RESPONSE_CHARS = 20_000;

    private final ObjectMapper objectMapper;

    public HttpRequestTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "http_request";
    }

    @Override
    public String getDescription() {
        return "调用外部 HTTP API。支持 GET/POST/PUT/DELETE,可自定义请求头和 JSON body,返回响应状态码与文本内容。用于查询公开 API、触发 webhook 等。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> url = new LinkedHashMap<>();
        url.put("type", "string");
        url.put("description", "完整请求 URL,如 https://api.example.com/data");

        Map<String, Object> method = new LinkedHashMap<>();
        method.put("type", "string");
        method.put("enum", List.of("GET", "POST", "PUT", "DELETE"));
        method.put("description", "HTTP 方法,默认 GET");

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("type", "object");
        headers.put("description", "请求头键值对(可选)");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "string");
        body.put("description", "请求体(JSON 字符串,POST/PUT 时使用,可选)");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("url", url);
        props.put("method", method);
        props.put("headers", headers);
        props.put("body", body);
        schema.put("properties", props);
        schema.put("required", List.of("url"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String url = arguments.get("url") == null ? "" : String.valueOf(arguments.get("url")).trim();
        if (url.isEmpty()) {
            return ToolResult.error("url 参数不能为空");
        }
        String method = arguments.get("method") == null ? "GET" : String.valueOf(arguments.get("method")).toUpperCase();
        if (!List.of("GET", "POST", "PUT", "DELETE").contains(method)) {
            return ToolResult.error("不支持的方法: " + method);
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15));

            @SuppressWarnings("unchecked")
            Map<String, Object> headers = arguments.get("headers") instanceof Map
                    ? (Map<String, Object>) arguments.get("headers") : Map.of();
            headers.forEach((k, v) -> builder.header(k, String.valueOf(v)));

            String body = arguments.get("body") == null ? null : String.valueOf(arguments.get("body"));
            HttpRequest.BodyPublisher publisher = body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();
            builder.method(method, publisher);

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            String respBody = resp.body() == null ? "" : resp.body();
            if (respBody.length() > MAX_RESPONSE_CHARS) {
                respBody = respBody.substring(0, MAX_RESPONSE_CHARS) + "\n...[响应过长,已截断]";
            }
            String pretty = maybePretty(respBody);
            return ToolResult.success(String.format("HTTP %s %s → %d\n%s", method, url, resp.statusCode(), pretty));
        } catch (Exception e) {
            log.error("HTTP 请求失败: url={}", url, e);
            return ToolResult.error("HTTP 请求失败: " + e.getMessage());
        }
    }

    private String maybePretty(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return body;
        }
    }
}
