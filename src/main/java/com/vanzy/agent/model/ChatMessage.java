package com.vanzy.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话消息(兼容 OpenAI / DeepSeek 格式)
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    /** 角色: system / user / assistant / tool */
    private String role;

    /** 消息内容(tool 角色时为工具执行结果) */
    private String content;

    /** assistant 消息的工具调用列表(当 LLM 决定调用工具时) */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    /** tool 角色消息: 对应的 tool_call_id */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /** tool 角色消息: 工具名 */
    private String name;

    public static ChatMessage system(String content) {
        return ChatMessage.builder().role("system").content(content).build();
    }

    public static ChatMessage user(String content) {
        return ChatMessage.builder().role("user").content(content).build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder().role("assistant").content(content).build();
    }

    /** assistant 消息: 携带工具调用(无文本内容) */
    public static ChatMessage assistantWithToolCalls(List<ToolCall> toolCalls) {
        return ChatMessage.builder().role("assistant").toolCalls(toolCalls).build();
    }

    /** tool 角色消息: 返回工具执行结果 */
    public static ChatMessage toolResult(String toolCallId, String name, String content) {
        return ChatMessage.builder().role("tool").toolCallId(toolCallId).name(name).content(content).build();
    }

    /**
     * 工具调用(OpenAI function calling 格式)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCall {
        /** 调用 ID(LLM 生成,回传 tool 消息时需对应) */
        private String id;
        /** 类型,固定 "function" */
        private String type;
        /** 函数调用详情 */
        private Function function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        /** 工具名 */
        private String name;
        /** 参数(JSON 字符串) */
        private String arguments;
    }
}

