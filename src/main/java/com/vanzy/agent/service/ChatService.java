package com.vanzy.agent.service;

import com.vanzy.agent.model.ChatRequest;
import com.vanzy.agent.model.ChatResponse;
import com.vanzy.agent.model.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * 核心对话服务: 协调记忆、RAG、LLM、工具调用四大组件
 *
 * @author VanzyLiu
 */
public interface ChatService {

    /**
     * 同步对话: 一次性返回完整回复
     *
     * @param request 对话请求
     * @return 完整响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式对话: 通过 SSE 返回事件流
     * 事件类型包括 Token(文本)、ToolCall(工具调用)、ToolResult(工具结果)、Error(错误)
     *
     * @param request 对话请求
     * @return 事件流 Flux
     */
    Flux<StreamEvent> chatStream(ChatRequest request);

    /**
     * 清空指定会话的历史
     *
     * @param sessionId 会话 ID
     */
    void clearSession(String sessionId);

    /**
     * 解析会话 ID: 入参为空则新建,非空则原样返回
     *
     * @param sessionId 请求中的 sessionId(可为空)
     * @return 实际使用的 sessionId
     */
    String resolveSessionId(String sessionId);
}
