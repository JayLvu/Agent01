package com.vanzy.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 *
 * 自动收集所有 Spring 容器中的 Tool Bean,提供按名查找与列出 schema。
 * schema 列表用于构建 DeepSeek/OpenAI function calling 的 tools 参数。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            tools.put(tool.getName(), tool);
            log.info("注册工具: {} - {}", tool.getName(), tool.getDescription());
        }
        log.info("共注册 {} 个工具", tools.size());
    }

    /** 按名查找工具 */
    public Tool get(String name) {
        return tools.get(name);
    }

    /** 是否存在指定工具 */
    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** 列出所有工具 */
    public List<Tool> list() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 生成 OpenAI function calling 的 tools 参数
     * 格式: [{"type":"function","function":{"name":..,"description":..,"parameters":{..}}}, ...]
     */
    public List<Map<String, Object>> buildToolsSchema() {
        List<Map<String, Object>> schema = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParametersSchema());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "function");
            entry.put("function", function);
            schema.add(entry);
        }
        return schema;
    }
}
