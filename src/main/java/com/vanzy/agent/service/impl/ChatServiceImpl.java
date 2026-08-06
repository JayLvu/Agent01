package com.vanzy.agent.service.impl;

import com.vanzy.agent.client.DeepSeekClient;
import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.config.DeepSeekProperties;
import com.vanzy.agent.exception.AgentException;
import com.vanzy.agent.model.ChatMessage;
import com.vanzy.agent.model.ChatRequest;
import com.vanzy.agent.model.ChatResponse;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekResponse;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekStreamChunk;
import com.vanzy.agent.model.DocumentChunk;
import com.vanzy.agent.rag.RagService;
import com.vanzy.agent.service.ChatService;
import com.vanzy.agent.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 核心对话服务实现
 *
 * 完整流程:
 * 1. 校验 sessionId, 无则新建
 * 2. 从 MemoryService 获取历史消息
 * 3. (可选)从 RagService 检索相关文档,拼装 system prompt
 * 4. 调用 DeepSeek 获取回复(同步 / 流式)
 * 5. 将本轮 user + assistant 消息存回 MemoryService
 *
 * @author VanzyLiu
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个友好、专业的 AI 助手。请用中文清晰、准确地回答用户问题。";

    private final DeepSeekClient deepSeekClient;
    private final MemoryService memoryService;
    private final RagService ragService;
    private final DeepSeekProperties deepSeekProperties;
    private final AgentProperties agentProperties;

    public ChatServiceImpl(DeepSeekClient deepSeekClient, MemoryService memoryService,
                           RagService ragService, DeepSeekProperties deepSeekProperties,
                           AgentProperties agentProperties) {
        this.deepSeekClient = deepSeekClient;
        this.memoryService = memoryService;
        this.ragService = ragService;
        this.deepSeekProperties = deepSeekProperties;
        this.agentProperties = agentProperties;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String sessionId = ensureSessionId(request.getSessionId());

        // 1. 构建完整消息列表
        List<ChatMessage> messages = buildMessages(request, sessionId);

        // 2. 调用 LLM
        DeepSeekResponse llmResp = deepSeekClient.chat(messages)
                .block(Duration.ofSeconds(deepSeekProperties.getTimeout().getSeconds()));

        if (llmResp == null || CollectionUtils.isEmpty(llmResp.getChoices())) {
            throw new AgentException("DeepSeek 返回空响应");
        }
        String content = llmResp.getChoices().get(0).getMessage().getContent();

        // 3. 持久化本轮对话到记忆
        memoryService.saveMessage(sessionId, ChatMessage.user(request.getMessage()));
        memoryService.saveMessage(sessionId, ChatMessage.assistant(content));

        long duration = System.currentTimeMillis() - start;
        log.info("同步对话完成: session={}, duration={}ms, tokens={}",
                sessionId, duration,
                llmResp.getUsage() != null ? llmResp.getUsage().getTotalTokens() : -1);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .content(content)
                .model(llmResp.getModel())
                .durationMs(duration)
                .timestamp(LocalDateTime.now())
                .usage(buildUsage(llmResp))
                .build();
    }

    @Override
    public Flux<String> chatStream(ChatRequest request) {
        String sessionId = ensureSessionId(request.getSessionId());
        List<ChatMessage> messages = buildMessages(request, sessionId);

        // 流式场景: 先保存 user 消息,assistant 消息流式收集后保存
        memoryService.saveMessage(sessionId, ChatMessage.user(request.getMessage()));

        StringBuilder fullReply = new StringBuilder();
        return deepSeekClient.chatStream(messages)
                .map(this::extractDeltaContent)
                .filter(StringUtils::hasText)
                .doOnNext(fullReply::append)
                .doOnComplete(() -> {
                    memoryService.saveMessage(sessionId, ChatMessage.assistant(fullReply.toString()));
                    log.info("流式对话完成: session={}", sessionId);
                })
                .doOnError(e -> log.error("流式对话失败: session={}", sessionId, e));
    }

    @Override
    public void clearSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new AgentException("sessionId 不能为空");
        }
        memoryService.clearHistory(sessionId);
    }

    private String ensureSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : memoryService.createSession();
    }

    /**
     * 构建完整消息列表: system + history + (RAG context) + user
     */
    private List<ChatMessage> buildMessages(ChatRequest request, String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. system prompt(可由用户临时覆盖)
        String systemPrompt = StringUtils.hasText(request.getSystemPrompt())
                ? request.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT;

        // 2. RAG 上下文增强(若启用)
        boolean enableRag = request.getEnableRag() == null || request.getEnableRag();
        if (enableRag && agentProperties.getRag().isEnabled()) {
            String ragContext = ragService.retrieveContext(request.getMessage());
            if (StringUtils.hasText(ragContext)) {
                systemPrompt = systemPrompt + "\n\n" + ragContext;
            }
        }
        messages.add(ChatMessage.system(systemPrompt));

        // 3. 历史对话
        messages.addAll(memoryService.getHistory(sessionId));

        // 4. 当前用户消息(同步场景: 还未保存,这里追加)
        // 注: 流式场景在外层已保存,但消息列表里仍需追加
        messages.add(ChatMessage.user(request.getMessage()));

        return messages;
    }

    private String extractDeltaContent(DeepSeekStreamChunk chunk) {
        if (chunk == null || CollectionUtils.isEmpty(chunk.getChoices())) {
            return "";
        }
        DeepSeekStreamChunk.Choice choice = chunk.getChoices().get(0);
        return choice.getDelta() != null ? choice.getDelta().getContent() : "";
    }

    private ChatResponse.Usage buildUsage(DeepSeekResponse llmResp) {
        if (llmResp.getUsage() == null) return null;
        DeepSeekResponse.Usage u = llmResp.getUsage();
        return ChatResponse.Usage.builder()
                .promptTokens(u.getPromptTokens())
                .completionTokens(u.getCompletionTokens())
                .totalTokens(u.getTotalTokens())
                .build();
    }
}
