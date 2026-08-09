package com.vanzy.agent.tool.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列出 workspace 沙箱内指定目录的文件与子目录
 *
 * 所有相对路径以 {@link AgentProperties.FileToolsConfig#getWorkspaceDir()} 为根,
 * 不允许通过 ../ 跳出工作目录以保证安全性。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class FileListTool implements Tool {

    private final AgentProperties.FileToolsConfig config;

    public FileListTool(AgentProperties agentProperties) {
        this.config = agentProperties.getFile();
    }

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public String getDescription() {
        return "列出 workspace 沙箱内指定目录下的文件和子目录,用于查询当前有哪些文件、了解路径结构。" +
                "所有路径都被限制在安全工作目录内,不会暴露系统其他位置。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("type", "string");
        path.put("description", "相对 workspace 根目录的目录路径,留空或使用 '.' 表示根目录。例如 'notes' 或 'project/src'");

        Map<String, Object> recursive = new LinkedHashMap<>();
        recursive.put("type", "boolean");
        recursive.put("description", "是否递归列出子目录,默认 false(仅当前层级)");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", path);
        props.put("recursive", recursive);
        schema.put("properties", props);

        schema.put("required", List.of());
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (!config.isEnabled()) {
            return ToolResult.error("文件操作工具已被禁用(agent.file.enabled=false)");
        }

        try {
            Path sandboxRoot = getSandboxRoot();
            String relPath = arguments.get("path") == null ? "" : String.valueOf(arguments.get("path")).trim();
            boolean recursive = arguments.get("recursive") != null && Boolean.TRUE.equals(Boolean.valueOf(String.valueOf(arguments.get("recursive"))));

            Path target = resolveWithinSandbox(sandboxRoot, relPath);
            if (!Files.exists(target)) {
                return ToolResult.error("目录不存在: " + toDisplay(sandboxRoot, target));
            }
            if (!Files.isDirectory(target)) {
                return ToolResult.error("路径不是目录: " + toDisplay(sandboxRoot, target));
            }

            // 1) 先收集条目(便于生成表格 + 缩进文本两种视图)
            List<FileEntry> entries = new ArrayList<>();
            listEntries(target, sandboxRoot, 0, recursive, entries);

            StringBuilder sb = new StringBuilder();
            String displayPath = toDisplay(sandboxRoot, target);

            // --- Markdown 表格视图(主视图): 前后强制空行,避免被 LLM/Markdown 误解析为标题/hr ---
            sb.append("\n\n");
            sb.append("**目录**: `").append(displayPath).append("`  ");
            sb.append(recursive ? "(递归)" : "(仅当前层级)");
            sb.append("  **条目数**: ").append(entries.size()).append("\n\n");
            sb.append("| 类型 | 名称/路径 | 大小 | 最后修改时间 |\n");
            sb.append("| :--- | :--- | ---: | :--- |\n");
            for (FileEntry e : entries) {
                sb.append("| ");
                if (e.isDir) {
                    sb.append("📁 目录 ");
                } else {
                    sb.append("📄 文件 ");
                }
                sb.append("| `").append(escapeMd(e.displayPath)).append("` | ");
                if (e.isDir) {
                    sb.append("— | — ");
                } else {
                    sb.append(humanSize(e.sizeBytes)).append(" | ").append(e.modified).append(' ');
                }
                sb.append("|\n");
            }
            if (entries.isEmpty()) {
                sb.append("| — | _(空目录)_ | — | — |\n");
            }
            sb.append('\n');

            // --- 纯文本缩进视图(折叠,便于 LLM 感知层级) ---
            sb.append("\n<details><summary>缩进视图(含层级)</summary>\n\n```text\n");
            sb.append("[目录] ").append(displayPath).append('\n');
            for (FileEntry e : entries) {
                sb.append("  ".repeat(e.depth));
                if (e.isDir) {
                    sb.append("[DIR]  ").append(e.name).append("/\n");
                } else {
                    sb.append(String.format("[FILE] %-40s  %6d KB  %s%n",
                            e.name, e.sizeBytes / 1024, e.modified));
                }
            }
            sb.append("```\n</details>\n\n");

            String output = sb.toString();
            log.info("list_files 成功: {} 条目={}", displayPath, entries.size());
            return ToolResult.success(output);
        } catch (SecurityException e) {
            return ToolResult.error("路径越权: " + e.getMessage());
        } catch (Exception e) {
            log.error("list_files 异常", e);
            return ToolResult.error(e.getMessage());
        }
    }

    // --- 条目数据结构 + 递归收集 ---
    private static class FileEntry {
        final boolean isDir;
        final String name;
        final String displayPath; // 相对沙箱根的完整路径
        final int depth;            // 相对本次目标目录的深度
        final long sizeBytes;
        final String modified;

        FileEntry(boolean isDir, String name, String displayPath, int depth, long sizeBytes, String modified) {
            this.isDir = isDir;
            this.name = name;
            this.displayPath = displayPath;
            this.depth = depth;
            this.sizeBytes = sizeBytes;
            this.modified = modified;
        }
    }

    private void listEntries(Path dir, Path sandboxRoot, int depth, boolean recursive, List<FileEntry> out) {
        File[] items = dir.toFile().listFiles();
        if (items == null) return;
        // 排序: 目录在前,文件在后;各自按名称升序
        java.util.Arrays.sort(items, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (File item : items) {
            String relToSandbox = toDisplay(sandboxRoot, item.toPath());
            String modified = LocalDateTime
                    .ofInstant(Instant.ofEpochMilli(item.lastModified()), ZoneId.systemDefault())
                    .format(fmt);
            if (item.isDirectory()) {
                out.add(new FileEntry(true, item.getName(), relToSandbox, depth, 0L, modified));
                if (recursive) {
                    listEntries(item.toPath(), sandboxRoot, depth + 1, true, out);
                }
            } else {
                out.add(new FileEntry(false, item.getName(), relToSandbox, depth, item.length(), modified));
            }
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String escapeMd(String s) {
        // 只转义表格关键字符: 竖线/反斜杠; 保留路径显示可读性
        return s.replace("\\", "\\\\").replace("|", "\\|").replace("`", "\\`");
    }

    static Path getSandboxRoot() {
        Path root = Paths.get("").toAbsolutePath().resolve("workspace").normalize();
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                // 初始化示例文件,方便测试
                Files.writeString(root.resolve("README.md"), "# Workspace 沙箱\n\n这是文件操作工具的安全工作目录,所有文件读写都被限制在此处。\n", java.nio.charset.StandardCharsets.UTF_8);
                Path notesDir = Files.createDirectories(root.resolve("notes"));
                Files.writeString(notesDir.resolve("2026-08-06.txt"),
                        "[日期] 2026-08-06\n[主题] 工具调用模式测试\n\n今日完成:\n1. LLM 工具调用框架开发\n2. Shell / 计算器 / 时间工具上线\n3. 文件操作工具(列表/读取/写入)上线\n",
                        java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return root;
    }

    static Path resolveWithinSandbox(Path sandboxRoot, String relPath) {
        Path base = sandboxRoot.toAbsolutePath().normalize();
        String clean = StringUtils.hasText(relPath) ? relPath.replace("\\", "/") : ".";
        if (clean.startsWith("/")) clean = clean.substring(1);
        Path resolved = base.resolve(clean).normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("路径越出 workspace 沙箱范围: " + relPath);
        }
        return resolved;
    }

    static String toDisplay(Path sandboxRoot, Path actual) {
        Path rel = sandboxRoot.relativize(actual);
        String s = rel.toString();
        return s.isEmpty() ? "/" : "/" + s.replace("\\", "/");
    }
}
