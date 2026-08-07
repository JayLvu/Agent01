package com.vanzy.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 是否执行成功 */
    private boolean success;

    /** 结果内容(将作为 tool 角色消息回传给 LLM) */
    private String content;

    /** 执行耗时(ms) */
    private long durationMs;

    public static ToolResult success(String content) {
        return ToolResult.builder().success(true).content(content).build();
    }

    public static ToolResult error(String message) {
        return ToolResult.builder().success(false).content("工具执行失败: " + message).build();
    }
}
