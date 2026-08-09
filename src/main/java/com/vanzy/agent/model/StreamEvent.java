package com.vanzy.agent.model;

/**
 * 流式事件: 对话流中的各种事件类型
 *
 * 用于在工具调用模式下,统一传递 token、工具调用、工具结果等结构化事件。
 *
 * @author VanzyLiu
 */
public sealed interface StreamEvent
        permits StreamEvent.Token, StreamEvent.ToolCall, StreamEvent.ToolResult, StreamEvent.Error {

    /** 普通文本 token */
    record Token(String content) implements StreamEvent {}

    /** LLM 决定调用工具(startedAt: 工具开始执行的毫秒时间戳) */
    record ToolCall(String toolName, String arguments, String callId, long startedAt) implements StreamEvent {}

    /** 工具执行结果(startedAt: 开始毫秒时间戳, finishedAt: 结束毫秒时间戳) */
    record ToolResult(String toolName, String callId, String result, boolean success, long durationMs,
                      long startedAt, long finishedAt) implements StreamEvent {}

    /** 错误 */
    record Error(String message) implements StreamEvent {}
}
