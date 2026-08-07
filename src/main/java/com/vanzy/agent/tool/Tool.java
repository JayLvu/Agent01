package com.vanzy.agent.tool;

import java.util.Map;

/**
 * 工具接口: LLM 可调用的工具(函数)
 *
 * 实现此接口并注册为 Spring Bean,ToolRegistry 会自动收集。
 * 工具 schema 遵循 OpenAI Function Calling 格式。
 *
 * @author VanzyLiu
 */
public interface Tool {

    /** 工具名称(LLM 调用时使用,需唯一) */
    String getName();

    /** 工具描述(供 LLM 决策时参考) */
    String getDescription();

    /**
     * 参数 JSON Schema(OpenAI 函数参数格式)
     * 示例: {"type":"object","properties":{"cmd":{"type":"string","description":"命令"}},"required":["cmd"]}
     */
    Map<String, Object> getParametersSchema();

    /**
     * 执行工具
     *
     * @param arguments LLM 传入的参数(已从 JSON 解析为 Map)
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> arguments);
}
