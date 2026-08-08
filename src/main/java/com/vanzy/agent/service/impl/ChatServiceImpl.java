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
import com.vanzy.agent.model.StreamEvent;
import com.vanzy.agent.model.AttachmentFile;
import com.vanzy.agent.rag.RagService;
import com.vanzy.agent.service.ChatService;
import com.vanzy.agent.service.MemoryService;
import com.vanzy.agent.skill.SkillService;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolRegistry;
import com.vanzy.agent.tool.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final DeepSeekProperties deepSeekProperties;
    private final AgentProperties agentProperties;
    private final SkillService skillService;

    public ChatServiceImpl(DeepSeekClient deepSeekClient, MemoryService memoryService,
                           RagService ragService, ToolRegistry toolRegistry,
                           ObjectMapper objectMapper, DeepSeekProperties deepSeekProperties,
                           AgentProperties agentProperties, SkillService skillService) {
        this.deepSeekClient = deepSeekClient;
        this.memoryService = memoryService;
        this.ragService = ragService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.deepSeekProperties = deepSeekProperties;
        this.agentProperties = agentProperties;
        this.skillService = skillService;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String sessionId = ensureSessionId(request.getSessionId());
        List<ChatMessage> messages = buildMessages(request, sessionId);
        String savedUserMsg = buildUserMessageWithAttachments(request.getMessage(), request.getAttachments());
        memoryService.saveMessage(sessionId, ChatMessage.user(savedUserMsg));

        boolean toolsEnabled = Boolean.TRUE.equals(request.getEnableTools())
                && agentProperties.getTools().isEnabled();

        String content;
        DeepSeekResponse lastResp;
        if (toolsEnabled) {
            SyncToolResult r = chatWithToolsSync(sessionId, messages);
            content = r.content();
            lastResp = r.lastResp();
        } else {
            lastResp = deepSeekClient.chat(messages)
                    .block(Duration.ofSeconds(deepSeekProperties.getTimeout().getSeconds()));
            if (lastResp == null || CollectionUtils.isEmpty(lastResp.getChoices())) {
                throw new AgentException("DeepSeek 返回空响应");
            }
            content = lastResp.getChoices().get(0).getMessage().getContent();
            if (content == null) content = "";
        }

        memoryService.saveMessage(sessionId, ChatMessage.assistant(content));

        long duration = System.currentTimeMillis() - start;
        log.info("同步对话完成: session={}, duration={}ms, tools={}, tokens={}",
                sessionId, duration, toolsEnabled,
                lastResp != null && lastResp.getUsage() != null ? lastResp.getUsage().getTotalTokens() : -1);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .content(content)
                .model(lastResp != null ? lastResp.getModel() : null)
                .durationMs(duration)
                .timestamp(LocalDateTime.now())
                .usage(lastResp != null ? buildUsage(lastResp) : null)
                .build();
    }

    /**
     * 同步版工具调用循环(与流式逻辑等价,只是把最终文本聚合返回)
     */
    private SyncToolResult chatWithToolsSync(String sessionId, List<ChatMessage> messages) {
        List<Map<String, Object>> toolsSchema = toolRegistry.buildToolsSchema();
        Duration timeout = Duration.ofSeconds(deepSeekProperties.getTimeout().getSeconds());

        List<ChatMessage> working = new ArrayList<>(messages);
        DeepSeekResponse lastResp = null;
        int iter = 0;
        while (true) {
            iter++;
            DeepSeekResponse resp = deepSeekClient.chat(working, toolsSchema).block(timeout);
            if (resp == null || CollectionUtils.isEmpty(resp.getChoices())) {
                throw new AgentException("DeepSeek 返回空响应");
            }
            lastResp = resp;
            DeepSeekResponse.Choice choice = resp.getChoices().get(0);
            ChatMessage msg = choice.getMessage();
            List<ChatMessage.ToolCall> toolCalls = msg.getToolCalls();

            if (toolCalls != null && !toolCalls.isEmpty()) {
                working.add(ChatMessage.assistantWithToolCalls(toolCalls));
                for (ChatMessage.ToolCall tc : toolCalls) {
                    String toolName = tc.getFunction() != null ? tc.getFunction().getName() : "";
                    String argsJson = tc.getFunction() != null ? tc.getFunction().getArguments() : "{}";
                    ToolResult result = executeToolCall(toolName, argsJson);
                    working.add(ChatMessage.toolResult(tc.getId(), toolName, result.getContent()));
                }
                continue;
            }
            String content = msg.getContent();
            if (content == null) content = "";
            log.info("同步工具调用对话完成: session={}, 迭代={}", sessionId, iter);
            return new SyncToolResult(content, lastResp);
        }
    }

    private record SyncToolResult(String content, DeepSeekResponse lastResp) {}

    @Override
    public Flux<StreamEvent> chatStream(ChatRequest request) {
        String sessionId = ensureSessionId(request.getSessionId());
        List<ChatMessage> messages = buildMessages(request, sessionId);

        // 流式场景: 先保存 user 消息,assistant 消息流式收集后保存
        String savedUserMsg = buildUserMessageWithAttachments(request.getMessage(), request.getAttachments());
        memoryService.saveMessage(sessionId, ChatMessage.user(savedUserMsg));

        boolean toolsEnabled = Boolean.TRUE.equals(request.getEnableTools())
                && agentProperties.getTools().isEnabled();

        if (toolsEnabled) {
            return chatWithToolsFlow(sessionId, messages);
        }
        return chatPlainStream(sessionId, messages);
    }

    /**
     * 普通流式对话(无工具): 原 token 流,包装为 Token 事件
     */
    private Flux<StreamEvent> chatPlainStream(String sessionId, List<ChatMessage> messages) {
        StringBuilder fullReply = new StringBuilder();
        return deepSeekClient.chatStream(messages)
                .<StreamEvent>handle((chunk, sink) -> {
                    String token = extractDeltaContent(chunk);
                    if (StringUtils.hasText(token)) {
                        sink.next(new StreamEvent.Token(token));
                    }
                })
                .doOnNext(e -> fullReply.append(((StreamEvent.Token) e).content()))
                .doOnComplete(() -> {
                    memoryService.saveMessage(sessionId, ChatMessage.assistant(fullReply.toString()));
                    log.info("流式对话完成: session={}", sessionId);
                })
                .doOnError(e -> log.error("流式对话失败: session={}", sessionId, e))
                .onErrorResume(e -> Flux.just(new StreamEvent.Error(e.getMessage())));
    }

    /**
     * 工具调用流式对话: 同步循环 + 事件推送
     *
     * 流程:
     * 1. 带 tools 调用 LLM(同步)
     * 2. 若返回 tool_calls: 推送 ToolCall 事件 -> 执行工具 -> 推送 ToolResult 事件 -> 回传结果 -> 回到步骤1
     * 3. 若返回纯文本: 推送 Token 事件,结束
     * 4. 达到最大迭代次数仍未完成: 推送错误
     */
    private Flux<StreamEvent> chatWithToolsFlow(String sessionId, List<ChatMessage> messages) {
        List<Map<String, Object>> toolsSchema = toolRegistry.buildToolsSchema();
        Duration timeout = Duration.ofSeconds(deepSeekProperties.getTimeout().getSeconds());

        return Flux.<StreamEvent>create(sink -> {
            List<ChatMessage> working = new ArrayList<>(messages);
            int iter = 0;
            try {
                while (true) {
                    iter++;
                    DeepSeekResponse resp = deepSeekClient.chat(working, toolsSchema).block(timeout);
                    if (resp == null || CollectionUtils.isEmpty(resp.getChoices())) {
                        sink.next(new StreamEvent.Error("DeepSeek 返回空响应"));
                        break;
                    }
                    DeepSeekResponse.Choice choice = resp.getChoices().get(0);
                    ChatMessage msg = choice.getMessage();
                    List<ChatMessage.ToolCall> toolCalls = msg.getToolCalls();

                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        working.add(ChatMessage.assistantWithToolCalls(toolCalls));

                        for (ChatMessage.ToolCall tc : toolCalls) {
                            String toolName = tc.getFunction() != null ? tc.getFunction().getName() : "";
                            String argsJson = tc.getFunction() != null ? tc.getFunction().getArguments() : "{}";
                            sink.next(new StreamEvent.ToolCall(toolName, argsJson, tc.getId()));

                            ToolResult result = executeToolCall(toolName, argsJson);
                            sink.next(new StreamEvent.ToolResult(toolName, tc.getId(), result.getContent(),
                                    result.isSuccess(), result.getDurationMs()));

                            working.add(ChatMessage.toolResult(tc.getId(), toolName, result.getContent()));
                        }
                        continue;
                    }

                    String content = msg.getContent();
                    log.info("最终回复: finishReason={}, contentLen={}", choice.getFinishReason(), content == null ? 0 : content.length());
                    if (content == null) content = "";
                    memoryService.saveMessage(sessionId, ChatMessage.assistant(content));
                    for (String chunk : splitToChunks(content, 8)) {
                        sink.next(new StreamEvent.Token(chunk));
                    }
                    log.info("工具调用对话完成: session={}, 迭代={}", sessionId, iter);
                    sink.complete();
                    return;
                }
            } catch (Exception e) {
                log.error("工具调用对话异常: session={}", sessionId, e);
                sink.next(new StreamEvent.Error(e.getMessage()));
            } finally {
                sink.complete();
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /** 执行单个工具调用 */
    private ToolResult executeToolCall(String toolName, String argsJson) {
        Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return ToolResult.error("未知工具: " + toolName);
        }
        try {
            Map<String, Object> args = StringUtils.hasText(argsJson)
                    ? objectMapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {})
                    : Map.of();
            log.info("调用工具: {} 参数: {}", toolName, argsJson);
            return tool.execute(args);
        } catch (Exception e) {
            return ToolResult.error("参数解析失败: " + e.getMessage());
        }
    }

    /** 将文本按指定字符数切分(模拟流式) */
    private List<String> splitToChunks(String text, int size) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(i + size, text.length())));
        }
        if (chunks.isEmpty()) chunks.add(text);
        return chunks;
    }

    @Override
    public void clearSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new AgentException("sessionId 不能为空");
        }
        memoryService.clearHistory(sessionId);
    }

    @Override
    public String resolveSessionId(String sessionId) {
        return ensureSessionId(sessionId);
    }

    private String ensureSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : memoryService.createSession();
    }

    /**
     * 构建完整消息列表: system + history + (RAG context) + user(含附件)
     */
    private List<ChatMessage> buildMessages(ChatRequest request, String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. system prompt(可由用户临时覆盖)
        String systemPrompt = StringUtils.hasText(request.getSystemPrompt())
                ? request.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT;

        // 2. Skill 注入(若启用): 把所有已启用 Skill 的内容追加到 System Prompt
        if (agentProperties.getSkill().isEnabled()) {
            String skillsPrompt = skillService.buildEnabledSkillsPrompt();
            if (StringUtils.hasText(skillsPrompt)) {
                systemPrompt = systemPrompt + skillsPrompt;
            }
        }

        // 3. RAG 上下文增强(若启用)
        boolean enableRag = request.getEnableRag() == null || request.getEnableRag();
        if (enableRag && agentProperties.getRag().isEnabled()) {
            String ragContext = ragService.retrieveContext(request.getMessage());
            if (StringUtils.hasText(ragContext)) {
                systemPrompt = systemPrompt + "\n\n" + ragContext;
            }
        }
        messages.add(ChatMessage.system(systemPrompt));

        // 4. 历史对话
        messages.addAll(memoryService.getHistory(sessionId));

        // 5. 当前用户消息 + 附件内容
        // 附件内容以"文件名 + 内容块"的形式插在用户消息前,让 LLM 知道这是用户提供的文件
        String userMessage = buildUserMessageWithAttachments(request.getMessage(), request.getAttachments());
        messages.add(ChatMessage.user(userMessage));

        return messages;
    }

    /**
     * 把附件内容以可读的格式拼接到用户消息前
     *
     * 输出形如:
     * ---
     * 附件文件[1]: report.md (12345 bytes)
     * ```
     * 这里是文件原始内容
     * ```
     * ---
     * 用户问题: xxxx
     */
    private String buildUserMessageWithAttachments(String message, List<AttachmentFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return message == null ? "" : message;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户上传了 ").append(attachments.size()).append(" 个附件文件, 内容如下:\n\n");
        int idx = 0;
        for (AttachmentFile a : attachments) {
            idx++;
            sb.append("--- 附件[").append(idx).append("]: ")
                    .append(a.getFileName() != null ? a.getFileName() : "未命名文件");
            if (a.getFileSize() != null) sb.append(" (").append(a.getFileSize()).append(" bytes)");
            sb.append(" ---\n");
            String content = a.getContent() == null ? "" : a.getContent();
            if (content.length() > agentProperties.getFile().getMaxReadBytes()) {
                content = content.substring(0, agentProperties.getFile().getMaxReadBytes())
                        + "\n...[文件过长,已截断]";
            }
            sb.append(content).append("\n\n");
        }
        sb.append("---\n请基于以上附件内容回答用户问题。\n\n用户问题: ");
        sb.append(message == null ? "" : message);
        return sb.toString();
    }

    private String extractDeltaContent(DeepSeekStreamChunk chunk) {
        if (chunk == null || CollectionUtils.isEmpty(chunk.getChoices())) {
            return "";
        }
        DeepSeekStreamChunk.Choice choice = chunk.getChoices().get(0);
        if (choice.getDelta() == null) return "";
        String content = choice.getDelta().getContent();
        return content != null ? content : "";
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
