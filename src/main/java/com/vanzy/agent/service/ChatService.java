package com.vanzy.agent.service;

import com.vanzy.agent.model.ChatRequest;
import com.vanzy.agent.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 核心对话服务: 协调记忆、RAG、LLM 三大组件
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
     * 流式对话: 通过 SSE 返回 token 流
     *
     * @param request 对话请求
     * @return 逐 token 的 Flux
     */
    Flux<String> chatStream(ChatRequest request);

    /**
     * 清空指定会话的历史
     *
     * @param sessionId 会话 ID
     */
    void clearSession(String sessionId);
}
