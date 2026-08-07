package com.vanzy.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 对话请求 DTO
 *
 * @author VanzyLiu
 */
@Data
public class ChatRequest {

    /** 会话 ID(为空则新建会话) */
    private String sessionId;

    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 8000, message = "单条消息不能超过 8000 字符")
    private String message;

    /** 是否启用 RAG 文档检索(默认 true) */
    private Boolean enableRag = true;

    /** 是否启用工具调用模式(LLM 自主决策调用工具,默认 false) */
    private Boolean enableTools = false;

    /** 是否流式响应(由 API 端点决定,此字段仅作记录) */
    private Boolean stream = false;

    /** 可选: 临时 system prompt(覆盖默认) */
    private String systemPrompt;

    /** 可选: 额外元数据(如用户标识、来源等) */
    private Map<String, Object> metadata;
}
