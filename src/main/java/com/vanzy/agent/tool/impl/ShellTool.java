package com.vanzy.agent.tool.impl;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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

        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-Command", command);
        pb.redirectErrorStream(true);
        if (StringUtils.hasText(toolConfig.getShell().getWorkingDir())) {
            pb.directory(new java.io.File(toolConfig.getShell().getWorkingDir()));
        }

        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.error("命令执行超时(" + timeoutMs + "ms)");
            }
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
