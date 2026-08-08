package com.vanzy.agent.tool.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell 脚本执行工具
 *
 * 在 Windows 上执行 PowerShell 命令,受配置开关与超时约束。
 * 安全提示: 仅在受信任环境启用,LLM 可能执行任意命令。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class ShellTool implements Tool {

    private final AgentProperties.ToolConfig toolConfig;

    public ShellTool(AgentProperties agentProperties) {
        this.toolConfig = agentProperties.getTools();
    }

    @Override
    public String getName() {
        return "execute_shell";
    }

    @Override
    public String getDescription() {
        return "在本机执行 Shell / PowerShell 命令并返回标准输出。可用于查看目录、运行脚本、查询系统信息等。请谨慎使用,避免执行破坏性命令。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("type", "string");
        cmd.put("description", "要执行的命令内容(Windows 环境下使用 PowerShell 语法)");
        Map<String, Object> timeout = new LinkedHashMap<>();
        timeout.put("type", "integer");
        timeout.put("description", "超时时间(毫秒),默认 10000");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("command", cmd);
        props.put("timeoutMs", timeout);
        schema.put("properties", props);

        schema.put("required", java.util.List.of("command"));
        return schema;
    }

    /**
     * 包装命令:
     * - 先切换控制台输出代码页到 65001(UTF-8), 避免 Windows PowerShell 用 GBK 输出中文乱码;
     * - 用 $OutputEncoding 强制 PowerShell 管道输出为 UTF-8;
     * - 最后显式输出 exit 码,防止重定向吞掉失败信息。
     *
     * 注意: 所有的 ; 都用 ``;`` 在 PowerShell 字符串里不是转义符,这里直接用原生分号即可。
     */
    private String wrapCommandForUtf8(String raw) {
        // chcp 65001 的输出会被捕获, 这里加 | Out-Null 去掉其干扰;
        // [Console]::OutputEncoding 强制控制台输出 UTF-8
        return "$OutputEncoding = [System.Text.Encoding]::UTF8;" +
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8;" +
                "chcp 65001 | Out-Null;" +
                raw;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (!toolConfig.getShell().isEnabled()) {
            return ToolResult.error("Shell 工具已被禁用(agent.tools.shell.enabled=false)");
        }

        String command = arguments.get("command") == null ? "" : String.valueOf(arguments.get("command"));
        if (!StringUtils.hasText(command)) {
            return ToolResult.error("command 参数不能为空");
        }

        int timeoutMs = toolConfig.getShell().getTimeoutMs();
        if (arguments.get("timeoutMs") != null) {
            try {
                timeoutMs = Integer.parseInt(String.valueOf(arguments.get("timeoutMs")));
            } catch (NumberFormatException ignored) {
            }
        }
        // 不超过配置上限
        timeoutMs = Math.min(timeoutMs, toolConfig.getShell().getTimeoutMs());

        long start = System.currentTimeMillis();
        log.info("执行 Shell 命令: {}", command);

        // 关键: 启动 PowerShell 时设置控制台输出代码页环境变量,保证子进程用 UTF-8 输出
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-OutputFormat", "Text",
                "-Command", wrapCommandForUtf8(command));
        pb.redirectErrorStream(true);
        // 让 PowerShell 的标准输出/错误输出都走 UTF-8 (Windows 新版 PS 会识别)
        pb.environment().put("OutputEncoding", "utf-8");
        pb.environment().put("LANG", "en_US.UTF-8");
        if (StringUtils.hasText(toolConfig.getShell().getWorkingDir())) {
            pb.directory(new java.io.File(toolConfig.getShell().getWorkingDir()));
        }

        try {
            Process process = pb.start();

            // 异步读取输出,避免 readLine() 阻塞导致超时失效
            // 注意: 强制使用 UTF-8 解码,不再使用 JVM 默认编码(Windows 下默认 GBK,会出现乱码)
            StringBuilder output = new StringBuilder();
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                } catch (Exception ignored) {
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                readerThread.interrupt();
                String partial = output.toString().trim();
                return ToolResult.error("命令执行超时(" + timeoutMs + "ms), 部分输出:\n" +
                        (StringUtils.hasText(partial) ? partial : "(无输出)"));
            }
            readerThread.join(2000);
            int exitCode = process.exitValue();
            long duration = System.currentTimeMillis() - start;
            String result = output.toString().trim();
            if (!StringUtils.hasText(result)) {
                result = "(无输出,退出码 " + exitCode + ")";
            }
            log.info("Shell 命令完成: exit={}, duration={}ms", exitCode, duration);
            return ToolResult.builder()
                    .success(exitCode == 0)
                    .content(result)
                    .durationMs(duration)
                    .build();
        } catch (Exception e) {
            log.error("Shell 命令执行异常", e);
            return ToolResult.error(e.getMessage());
        }
    }
}
