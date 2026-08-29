package com.vanzy.agent.tool.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览器自动化工具(browse_url): 抓取网页并提取标题、描述与正文文本。
 *
 * 实现为轻量级"网页抓取 + 可读性提取"(基于 jsoup),无需启动真实浏览器。
 * 适合"打开网页读取内容";若需点击/填表/截图等完整浏览器自动化,
 * 可在此基础上替换为 Playwright/Selenium 实现(同样实现 Tool 接口即可)。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class BrowserTool implements Tool {

    private final AgentProperties.BrowserConfig config;

    public BrowserTool(AgentProperties agentProperties) {
        this.config = agentProperties.getTools().getBrowser();
    }

    @Override
    public String getName() {
        return "browse_url";
    }

    @Override
    public String getDescription() {
        return "打开并读取指定网页的内容,提取标题、描述与正文文本。用于让 AI 浏览网页获取信息。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> url = new LinkedHashMap<>();
        url.put("type", "string");
        url.put("description", "要浏览的网页 URL,如 https://example.com");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("url", url);
        schema.put("properties", props);
        schema.put("required", List.of("url"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (!config.isEnabled()) {
            return ToolResult.error("浏览器自动化工具已被禁用(agent.tools.browser.enabled=false)");
        }
        String url = arguments.get("url") == null ? "" : String.valueOf(arguments.get("url")).trim();
        if (url.isEmpty()) {
            return ToolResult.error("url 参数不能为空");
        }
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Agent01/1.0")
                    .timeout(config.getTimeoutMs())
                    .followRedirects(true)
                    .get();

            String title = doc.title();
            String description = doc.select("meta[name=description]").attr("content");
            doc.select("script,style,noscript,iframe,svg,nav,footer,header").remove();
            String text = doc.body() != null ? doc.body().text() : "";
            if (text.length() > config.getMaxChars()) {
                text = text.substring(0, config.getMaxChars()) + "...[正文过长,已截断]";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[网页] ").append(url).append("\n");
            if (title != null && !title.isBlank()) sb.append("标题: ").append(title).append("\n");
            if (description != null && !description.isBlank()) sb.append("描述: ").append(description).append("\n");
            sb.append("正文:\n").append(text.isBlank() ? "(未提取到正文)" : text);
            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            log.error("浏览网页失败: url={}", url, e);
            return ToolResult.error("浏览网页失败: " + e.getMessage());
        }
    }
}
