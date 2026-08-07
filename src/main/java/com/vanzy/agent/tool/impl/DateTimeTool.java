package com.vanzy.agent.tool.impl;

import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 时间查询工具(内置 Skill 示例)
 *
 * @author VanzyLiu
 */
@Component
public class DateTimeTool implements Tool {

    @Override
    public String getName() {
        return "query_datetime";
    }

    @Override
    public String getDescription() {
        return "查询当前日期和时间。可指定时区(如 Asia/Shanghai、UTC),默认返回本机时区时间。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> tz = new LinkedHashMap<>();
        tz.put("type", "string");
        tz.put("description", "时区 ID,如 Asia/Shanghai、America/New_York、UTC。为空则用本机时区");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("timezone", tz);
        schema.put("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        ZoneId zone = ZoneId.systemDefault();
        Object tz = arguments.get("timezone");
        if (tz != null && !String.valueOf(tz).isBlank()) {
            try {
                zone = ZoneId.of(String.valueOf(tz));
            } catch (Exception e) {
                return ToolResult.error("无效的时区: " + tz);
            }
        }
        LocalDateTime now = LocalDateTime.now(zone);
        String formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + " (" + zone.getId() + ")";
        return ToolResult.success("当前时间: " + formatted);
    }
}
