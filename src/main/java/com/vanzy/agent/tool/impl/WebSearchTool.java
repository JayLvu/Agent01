package com.vanzy.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索工具(web_search)
 *
 * provider:
 * - duckduckgo: 使用 DuckDuckGo Instant Answer API(无需 API Key,结果较少)
 * - serper:     使用 Serper.dev(需 SERPER_API_KEY,结果更全)
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class WebSearchTool implements Tool {

    private final AgentProperties.SearchConfig config;
    private final ObjectMapper objectMapper;

    public WebSearchTool(AgentProperties agentProperties, ObjectMapper objectMapper) {
        this.config = agentProperties.getTools().getSearch();
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "联网搜索实时信息,返回标题、摘要与链接。默认使用 Bing(无需 Key),也可配置 duckduckgo/serper。用于查询新闻、最新动态、事实核查等需要联网的问题。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("type", "string");
        q.put("description", "搜索关键词或问题");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", q);
        schema.put("properties", props);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (!config.isEnabled()) {
            return ToolResult.error("联网搜索工具已被禁用(agent.tools.search.enabled=false)");
        }
        String query = arguments.get("query") == null ? "" : String.valueOf(arguments.get("query")).trim();
        if (query.isEmpty()) {
            return ToolResult.error("query 参数不能为空");
        }
        try {
            List<SearchHit> hits = switch (config.getProvider() == null ? "bing" : config.getProvider().toLowerCase()) {
                case "serper" -> searchSerper(query);
                case "duckduckgo" -> searchDuckDuckGo(query);
                default -> searchBing(query);
            };
            if (hits.isEmpty()) {
                return ToolResult.success("未找到与「" + query + "」相关的搜索结果。");
            }
            StringBuilder sb = new StringBuilder("关于「").append(query).append("」的搜索结果:\n\n");
            int n = 0;
            for (SearchHit hit : hits) {
                if (n++ >= config.getMaxResults()) break;
                sb.append("### ").append(n).append(". ").append(hit.title()).append("\n");
                sb.append("- 链接: ").append(hit.url()).append("\n");
                if (hit.snippet() != null && !hit.snippet().isBlank()) {
                    sb.append("- 摘要: ").append(hit.snippet()).append("\n");
                }
                sb.append("\n");
            }
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            log.error("联网搜索失败", e);
            return ToolResult.error("搜索失败: " + e.getMessage());
        }
    }

    private record SearchHit(String title, String url, String snippet) {}

    private List<SearchHit> searchDuckDuckGo(String query) throws Exception {
        String url = "https://api.duckduckgo.com/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&format=json&no_html=1&skip_disambig=1";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Agent01/1.0")
                .GET().build();
        String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        JsonNode root = objectMapper.readTree(body);
        List<SearchHit> hits = new ArrayList<>();
        String abs = root.path("AbstractText").asText("");
        if (!abs.isBlank()) {
            hits.add(new SearchHit(root.path("Heading").asText(""), root.path("AbstractURL").asText(""), abs));
        }
        for (JsonNode topic : root.path("RelatedTopics")) {
            if (topic.has("Topics")) {
                for (JsonNode sub : topic.path("Topics")) {
                    addHit(hits, sub);
                }
            } else {
                addHit(hits, topic);
            }
        }
        return hits;
    }

    private void addHit(List<SearchHit> hits, JsonNode node) {
        String text = node.path("Text").asText("");
        String url = node.path("FirstURL").asText("");
        if (!text.isBlank()) {
            // DuckDuckGo 的 Text 形如 "title - snippet"
            int idx = text.indexOf(" - ");
            String title = idx > 0 ? text.substring(0, idx) : text;
            String snippet = idx > 0 ? text.substring(idx + 3) : text;
            hits.add(new SearchHit(title, url, snippet));
        }
    }

    private List<SearchHit> searchBing(String query) throws Exception {
        String url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&setlang=zh-cn&count=10";
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000)
                .followRedirects(true)
                .get();
        List<SearchHit> hits = new ArrayList<>();
        for (Element el : doc.select("li.b_algo")) {
            Element a = el.selectFirst("h2 a");
            if (a == null) continue;
            Element p = el.selectFirst("p");
            String title = a.text();
            String link = a.absUrl("href");
            String snippet = p != null ? p.text() : "";
            if (!title.isBlank() && !link.isBlank()) {
                hits.add(new SearchHit(title, link, snippet));
            }
        }
        return hits;
    }

    private List<SearchHit> searchSerper(String query) throws Exception {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("serper provider 需要配置 agent.tools.search.api-key(SERPER_API_KEY)");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        Map<String, String> payload = Map.of("q", query, "gl", "cn", "hl", "zh-cn");
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://google.serper.dev/search"))
                .timeout(Duration.ofSeconds(15))
                .header("X-API-KEY", config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        JsonNode root = objectMapper.readTree(body);
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode item : root.path("organic")) {
            hits.add(new SearchHit(
                    item.path("title").asText(""),
                    item.path("link").asText(""),
                    item.path("snippet").asText("")));
        }
        return hits;
    }
}
