package com.vanzy.agent.service;

import com.vanzy.agent.model.ChatMessage;

import java.util.List;

/**
 * 对话记忆服务接口
 *
 * 职责: 管理每个会话(sessionId)的历史消息,用于多轮对话上下文。
 * 实现: Redis(生产) / 内存(降级)
 *
 * @author VanzyLiu
 */
public interface MemoryService {

    /**
     * 保存一条消息到会话历史
     *
     * @param sessionId 会话 ID
     * @param message   消息(user 或 assistant)
     */
    void saveMessage(String sessionId, ChatMessage message);

    /**
     * 获取会话历史消息(不含 system prompt,system 在 ChatService 中拼装)
     *
     * @param sessionId 会话 ID
     * @return 历史消息列表(按时间正序)
     */
    List<ChatMessage> getHistory(String sessionId);

    /**
     * 清空会话历史
     *
     * @param sessionId 会话 ID
     */
    void clearHistory(String sessionId);

    /**
     * 保存会话的压缩摘要(记忆摘要压缩)
     */
    void saveSummary(String sessionId, String summary);

    /**
     * 读取会话的压缩摘要
     */
    String getSummary(String sessionId);

    /**
     * 裁剪历史,只保留最近 keepRecent 条消息
     */
    void trimHistory(String sessionId, int keepRecent);

    /**
     * 创建新会话 ID
     *
     * @return 新的会话 ID
     */
    String createSession();
}
