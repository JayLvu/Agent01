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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.vanzy.agent.tool.impl.FileListTool.*;

/**
 * 读取 workspace 沙箱内的文本文件内容
 *
 * 支持 txt / md / json / log / csv 等文本格式;读取字节大小受 maxReadBytes 限制。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class FileReadTool implements Tool {

    private static final List<String> TEXT_EXT = Arrays.asList(
            ".txt", ".md", ".json", ".log", ".csv", ".xml", ".yml", ".yaml",
            ".java", ".py", ".js", ".ts", ".vue", ".c", ".cpp", ".h", ".cs",
            ".html", ".css", ".sql", ".sh", ".bat", ".ps1", ".ini", ".conf"
    );

    private final AgentProperties.FileToolsConfig config;

    public FileReadTool(AgentProperties agentProperties) {
        this.config = agentProperties.getFile();
    }

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "读取 workspace 沙箱内的文本文件内容(txt/md/json/log/java/vue/py 等),可按行范围读取。" +
                "路径为相对 workspace 根目录的相对路径,不得越出沙箱。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("type", "string");
        path.put("description", "文件相对 workspace 的路径,例如 'notes/daily.txt'");

        Map<String, Object> startLine = new LinkedHashMap<>();
        startLine.put("type", "integer");
        startLine.put("description", "起始行(从 1 开始),默认从头读取");

        Map<String, Object> endLine = new LinkedHashMap<>();
        endLine.put("type", "integer");
        endLine.put("description", "结束行(包含),默认读到文件尾或限制处");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", path);
        props.put("startLine", startLine);
        props.put("endLine", endLine);
        schema.put("properties", props);

        schema.put("required", List.of("path"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (!config.isEnabled()) {
            return ToolResult.error("文件操作工具已被禁用(agent.file.enabled=false)");
        }

        Object pathObj = arguments.get("path");
        if (pathObj == null || String.valueOf(pathObj).isBlank()) {
            return ToolResult.error("path 参数不能为空");
        }
        String relPath = String.valueOf(pathObj);

        Integer startLine = parseInt(arguments.get("startLine"));
        Integer endLine = parseInt(arguments.get("endLine"));

        try {
            Path sandboxRoot = getSandboxRoot();
            Path target = resolveWithinSandbox(sandboxRoot, relPath);

            if (!Files.exists(target)) {
                return ToolResult.error("文件不存在: " + toDisplay(sandboxRoot, target));
            }
            if (!Files.isRegularFile(target)) {
                return ToolResult.error("路径不是文件: " + toDisplay(sandboxRoot, target));
            }
            if (!isTextFile(target.toString())) {
                long size = Files.size(target);
                return ToolResult.error("非文本格式,暂不支持读取。文件大小: " + size + " 字节");
            }

            long sizeBytes = Files.size(target);
            if (sizeBytes > config.getMaxReadBytes()) {
                return ToolResult.error(String.format("文件过大(%d KB),超过上限 %d KB。请按行范围分片读取",
                        sizeBytes / 1024, config.getMaxReadBytes() / 1024));
            }

            List<String> allLines = Files.readAllLines(target, StandardCharsets.UTF_8);
            int from = startLine == null || startLine < 1 ? 1 : startLine;
            int to = endLine == null || endLine > allLines.size() ? allLines.size() : endLine;
            if (from > to || from > allLines.size()) {
                return ToolResult.success(String.format("(空内容) 总行数: %d, 读取范围: %d-%d",
                        allLines.size(), from, to));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[文件] ").append(toDisplay(sandboxRoot, target))
                    .append(" (总行数: ").append(allLines.size()).append(")  ")
                    .append(sizeBytes).append(" bytes\n");
            sb.append("---行 ").append(from).append("~").append(to).append("---\n");
            for (int i = from; i <= to; i++) {
                sb.append(String.format("%4d | ", i)).append(allLines.get(i - 1)).append('\n');
            }

            log.info("read_file 成功: {} 行 {}-{}", toDisplay(sandboxRoot, target), from, to);
            return ToolResult.success(sb.toString());
        } catch (SecurityException e) {
            return ToolResult.error("路径越权: " + e.getMessage());
        } catch (Exception e) {
            log.error("read_file 异常", e);
            return ToolResult.error("读取失败: " + e.getMessage());
        }
    }

    private static boolean isTextFile(String name) {
        String lower = name.toLowerCase();
        for (String ext : TEXT_EXT) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static Integer parseInt(Object o) {
        if (o == null) return null;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Path getSandboxRoot() {
        return Paths.get("").toAbsolutePath().resolve("workspace").normalize();
    }
}
