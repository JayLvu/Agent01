package com.vanzy.agent.tool.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.vanzy.agent.tool.impl.FileListTool.*;

/**
 * 在 workspace 沙箱内创建 / 覆盖 / 追加写入文本文件
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class FileWriteTool implements Tool {

    private final AgentProperties.FileToolsConfig config;

    public FileWriteTool(AgentProperties agentProperties) {
        this.config = agentProperties.getFile();
    }

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "在 workspace 沙箱中写入文本内容到指定文件。" +
                "支持新建文件(父目录自动创建)、覆盖已有文件或追加写入。" +
                "仅支持文本格式,文件大小受上限限制,路径不能越出沙箱。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("type", "string");
        path.put("description", "相对 workspace 的文件路径,例如 'notes/meeting.md'");

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "string");
        content.put("description", "要写入的文本内容(UTF-8 编码)");

        Map<String, Object> append = new LinkedHashMap<>();
        append.put("type", "boolean");
        append.put("description", "是否追加写入,默认 false(覆盖原文件)");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", path);
        props.put("content", content);
        props.put("append", append);
        schema.put("properties", props);

        schema.put("required", List.of("path", "content"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (!config.isEnabled()) {
            return ToolResult.error("文件操作工具已被禁用(agent.file.enabled=false)");
        }

        Object pathObj = arguments.get("path");
        Object contentObj = arguments.get("content");
        boolean append = arguments.get("append") != null && Boolean.TRUE.equals(Boolean.valueOf(String.valueOf(arguments.get("append"))));

        if (pathObj == null || String.valueOf(pathObj).isBlank()) {
            return ToolResult.error("path 参数不能为空");
        }
        if (contentObj == null) {
            return ToolResult.error("content 参数不能为空");
        }
        String relPath = String.valueOf(pathObj);
        String text = String.valueOf(contentObj);

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > config.getMaxWriteBytes()) {
            return ToolResult.error(String.format("内容过大(%d KB),超过写入上限 %d KB",
                    bytes.length / 1024, config.getMaxWriteBytes() / 1024));
        }

        try {
            Path sandboxRoot = getSandboxRoot();
            Path target = resolveWithinSandbox(sandboxRoot, relPath);

            // 父目录自动创建
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            long existingSize = Files.exists(target) ? Files.size(target) : 0;
            StandardOpenOption[] options;
            if (append) {
                options = new StandardOpenOption[]{
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
                };
            } else {
                options = new StandardOpenOption[]{
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
                };
            }
            Files.writeString(target, text, StandardCharsets.UTF_8, options);
            long finalSize = Files.size(target);

            String mode = append ? "追加" : (existingSize > 0 ? "覆盖" : "新建");
            String summary = String.format("%s成功: %s  大小: %d -> %d bytes  行数: %d",
                    mode, toDisplay(sandboxRoot, target),
                    existingSize, finalSize, countLines(text));
            log.info("write_file {}: {}", mode, toDisplay(sandboxRoot, target));
            return ToolResult.success(summary);
        } catch (SecurityException e) {
            return ToolResult.error("路径越权: " + e.getMessage());
        } catch (Exception e) {
            log.error("write_file 异常", e);
            return ToolResult.error("写入失败: " + e.getMessage());
        }
    }

    private static int countLines(String s) {
        if (s.isEmpty()) return 0;
        int c = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') c++;
        }
        return c;
    }

    private static Path getSandboxRoot() {
        return Paths.get("").toAbsolutePath().resolve("workspace").normalize();
    }
}
