package com.vanzy.agent.service.impl;

import com.vanzy.agent.client.DeepSeekClient;
import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.config.DeepSeekProperties;
import com.vanzy.agent.exception.AgentException;
import com.vanzy.agent.model.AttachmentFile;
import com.vanzy.agent.model.ChatMessage;
import com.vanzy.agent.model.ChatRequest;
import com.vanzy.agent.model.ChatResponse;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekResponse;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekStreamChunk;
import com.vanzy.agent.model.ModelConfig;
import com.vanzy.agent.model.StreamEvent;
import com.vanzy.agent.rag.RagService;
import com.vanzy.agent.routing.ModelRouter;
import com.vanzy.agent.service.AuditLogService;
import com.vanzy.agent.service.ChatService;
import com.vanzy.agent.service.MemoryService;
import com.vanzy.agent.service.TokenUsageService;
import com.vanzy.agent.skill.SkillService;
import com.vanzy.agent.tool.Tool;
import com.vanzy.agent.tool.ToolRegistry;
import com.vanzy.agent.tool.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 核心对话服务实现
 *
 * 完整流程:
 * 1. 多模型路由(按请求/关键词选择模型)
 * 2. 记忆摘要压缩(历史超预算自动压缩)
 * 3. 从 MemoryService 获取历史 + 摘要,组装 system prompt(Skill + RAG + 摘要)
 * 4. 调用 DeepSeek(同步 / 流式),工具循环带迭代上限 + 并行执行 + 取消
 * 5. 记录 Token 成本与审计日志
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
    private final ModelRouter modelRouter;
    private final TokenUsageService tokenUsageService;
    private final AuditLogService auditLogService;

    /** 工具执行线程池(并行工具调用 + 阻塞型工具不阻塞 reactor 线程) */
    private final ExecutorService toolExecutor =
            Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));

    public ChatServiceImpl(DeepSeekClient deepSeekClient, MemoryService memoryService,
                           RagService ragService, ToolRegistry toolRegistry,
                           ObjectMapper objectMapper, DeepSeekProperties deepSeekProperties,
                           AgentProperties agentProperties, SkillService skillService,
                           ModelRouter modelRouter, TokenUsageService tokenUsageService,
                           AuditLogService auditLogService) {
        this.deepSeekClient = deepSeekClient;
        this.memoryService = memoryService;
        this.ragService = ragService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.deepSeekProperties = deepSeekProperties;
        this.agentProperties = agentProperties;
        this.skillService = skillService;
        this.modelRouter = modelRouter;
        this.tokenUsageService = tokenUsageService;
        this.auditLogService = auditLogService;
    }

    @PreDestroy
    public void shutdown() {
        toolExecutor.shutdownNow();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        ModelConfig model = modelRouter.resolve(request);
        String sessionId = ensureSessionId(request.getSessionId());

        // 1. 记忆摘要压缩(若历史超预算)
        List<ChatMessage> history = loadHistoryWithCompression(sessionId);
        String summary = memoryService.getSummary(sessionId);

        // 2. 组装消息
        List<ChatMessage> messages = buildMessages(request, sessionId, history, summary);
        String savedUserMsg = buildUserMessageWithAttachments(request.getMessage(), request.getAttachments());
        memoryService.saveMessage(sessionId, ChatMessage.user(savedUserMsg));

        boolean toolsEnabled = Boolean.TRUE.equals(request.getEnableTools())
                && agentProperties.getTools().isEnabled();

        String content;
        UsageAcc usageAcc = new UsageAcc();
        if (toolsEnabled) {
            SyncToolResult r = chatWithToolsSync(sessionId, messages, model, usageAcc);
            content = r.content();
        } else {
            DeepSeekResponse lastResp = deepSeekClient.chat(messages, null, model)
                    .block(Duration.ofSeconds(model.getTimeout() != null ? model.getTimeout().getSeconds() : 60));
            if (lastResp == null || CollectionUtils.isEmpty(lastResp.getChoices())) {
                throw new AgentException("DeepSeek 返回空响应(可能超时)");
            }
            content = lastResp.getChoices().get(0).getMessage().getContent();
            if (content == null) content = "";
            usageAcc.add(lastResp.getUsage());
        }

        memoryService.saveMessage(sessionId, ChatMessage.assistant(content));

        long duration = System.currentTimeMillis() - start;
        double cost = tokenUsageService.computeCost(model, usageAcc.prompt, usageAcc.completion);
        tokenUsageService.record(sessionId, model.getName(), usageAcc.prompt, usageAcc.completion, usageAcc.total, model);
        auditLogService.log(AuditLogService.ACTION_CHAT, sessionId, null,
                "model=" + model.getName() + " tokens=" + usageAcc.total + " tools=" + toolsEnabled,
                true, duration);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .content(content)
                .model(model.getName())
                .durationMs(duration)
                .timestamp(LocalDateTime.now())
                .usage(ChatResponse.Usage.builder()
                        .promptTokens(usageAcc.prompt)
                        .completionTokens(usageAcc.completion)
                        .totalTokens(usageAcc.total)
                        .cost(cost)
                        .currency(agentProperties.getCost().getCurrency())
                        .build())
                .build();
    }

    /**
     * 同步版工具调用循环(带迭代上限,支持并行工具调用)
     */
    private SyncToolResult chatWithToolsSync(String sessionId, List<ChatMessage> messages,
                                             ModelConfig model, UsageAcc usageAcc) {
        List<Map<String, Object>> toolsSchema = toolRegistry.buildToolsSchema();
        Duration timeout = Duration.ofSeconds(model.getTimeout() != null ? model.getTimeout().getSeconds() : 60);

        List<ChatMessage> working = new ArrayList<>(messages);
        int maxIter = Math.max(1, agentProperties.getTools().getMaxIterations());

        for (int iter = 1; iter <= maxIter; iter++) {
            DeepSeekResponse resp = deepSeekClient.chat(working, toolsSchema, model).block(timeout);
            if (resp == null || CollectionUtils.isEmpty(resp.getChoices())) {
                throw new AgentException("DeepSeek 返回空响应(可能超时)");
            }
            usageAcc.add(resp.getUsage());
            DeepSeekResponse.Choice choice = resp.getChoices().get(0);
            ChatMessage msg = choice.getMessage();
            List<ChatMessage.ToolCall> toolCalls = msg.getToolCalls();

            if (toolCalls != null && !toolCalls.isEmpty()) {
                working.add(ChatMessage.assistantWithToolCalls(toolCalls));
                List<ChatMessage> toolResults = executeToolCalls(toolCalls, sessionId, null);
                working.addAll(toolResults);
                continue;
            }
            String content = msg.getContent();
            if (content == null) content = "";
            log.info("同步工具调用对话完成: session={}, 迭代={}", sessionId, iter);
            return new SyncToolResult(content);
        }
        // 达到迭代上限仍未结束: 返回降级提示
        String fallback = "已达到工具调用最大迭代次数(" + maxIter + "),已停止。请尝试更具体的问题。";
        return new SyncToolResult(fallback);
    }

    private record SyncToolResult(String content) {}

    @Override
    public Flux<StreamEvent> chatStream(ChatRequest request, Flux<String> cancelSignal) {
        ModelConfig model = modelRouter.resolve(request);
        String sessionId = ensureSessionId(request.getSessionId());

        List<ChatMessage> history = loadHistoryWithCompression(sessionId);
        String summary = memoryService.getSummary(sessionId);

        List<ChatMessage> messages = buildMessages(request, sessionId, history, summary);
        String savedUserMsg = buildUserMessageWithAttachments(request.getMessage(), request.getAttachments());
        memoryService.saveMessage(sessionId, ChatMessage.user(savedUserMsg));

        boolean toolsEnabled = Boolean.TRUE.equals(request.getEnableTools())
                && agentProperties.getTools().isEnabled();

        if (toolsEnabled) {
            return chatWithToolsFlow(sessionId, messages, model, cancelSignal);
        }
        return chatPlainStream(sessionId, messages, model, cancelSignal);
    }

    /**
     * 普通流式对话(无工具)
     */
    private Flux<StreamEvent> chatPlainStream(String sessionId, List<ChatMessage> messages,
                                              ModelConfig model, Flux<String> cancelSignal) {
        StringBuilder fullReply = new StringBuilder();
        AtomicReference<DeepSeekResponse.Usage> usageRef = new AtomicReference<>();

        Flux<StreamEvent> tokenFlux = deepSeekClient.chatStream(messages, null, model)
                .takeUntilOther(cancelSignal)
                .<StreamEvent>handle((chunk, sink) -> {
                    if (chunk.getUsage() != null) usageRef.set(chunk.getUsage());
                    String token = extractDeltaContent(chunk);
                    if (StringUtils.hasText(token)) {
                        sink.next(new StreamEvent.Token(token));
                    }
                })
                .doOnNext(e -> fullReply.append(((StreamEvent.Token) e).content()));

        return tokenFlux
                .concatWith(Mono.fromSupplier(() -> {
                    String content = fullReply.toString();
                    memoryService.saveMessage(sessionId, ChatMessage.assistant(content));
                    log.info("流式对话完成: session={}", sessionId);
                    return buildUsageEvent(sessionId, model, usageRef.get());
                }))
                .onErrorResume(e -> {
                    log.error("流式对话失败: session={}", sessionId, e);
                    return Flux.just(new StreamEvent.Error(e.getMessage()));
                });
    }

    /**
     * 工具调用流式对话: LLM 流式 + while 循环多轮工具调用(迭代上限 + 并行 + 取消)
     */
    private Flux<StreamEvent> chatWithToolsFlow(String sessionId, List<ChatMessage> messages,
                                                ModelConfig model, Flux<String> cancelSignal) {
        List<Map<String, Object>> toolsSchema = toolRegistry.buildToolsSchema();
        Duration timeout = Duration.ofSeconds(model.getTimeout() != null ? model.getTimeout().getSeconds() : 60);
        int maxIter = Math.max(1, agentProperties.getTools().getMaxIterations());

        return Flux.<StreamEvent>create(sink -> {
            List<ChatMessage> working = new ArrayList<>(messages);
            AtomicBoolean cancelled = new AtomicBoolean(false);
            cancelSignal.subscribe(v -> cancelled.set(true));
            UsageAcc usageAcc = new UsageAcc();
            boolean completed = false;
            try {
                int iter = 0;
                while (iter < maxIter) {
                    iter++;
                    if (cancelled.get()) {
                        sink.next(new StreamEvent.Cancelled("用户已停止生成"));
                        break;
                    }

                    StringBuilder assistantContent = new StringBuilder();
                    List<ChatMessage.ToolCall> accumToolCalls = new ArrayList<>();
                    AtomicReference<DeepSeekResponse.Usage> roundUsage = new AtomicReference<>();

                    deepSeekClient.chatStream(working, toolsSchema, model)
                            .takeUntilOther(cancelSignal)
                            .doOnNext(chunk -> {
                                if (chunk.getUsage() != null) roundUsage.set(chunk.getUsage());
                                if (CollectionUtils.isEmpty(chunk.getChoices())) return;
                                DeepSeekStreamChunk.Choice choice = chunk.getChoices().get(0);

                                String token = extractDeltaContent(chunk);
                                if (StringUtils.hasText(token)) {
                                    assistantContent.append(token);
                                    sink.next(new StreamEvent.Token(token));
                                }
                                if (choice.getDelta() == null
                                        || CollectionUtils.isEmpty(choice.getDelta().getToolCalls())) {
                                    return;
                                }
                                for (ChatMessage.ToolCall deltaTc : choice.getDelta().getToolCalls()) {
                                    int idx = deltaTc.getIndex() != null ? deltaTc.getIndex() : 0;
                                    while (accumToolCalls.size() <= idx) {
                                        accumToolCalls.add(ChatMessage.ToolCall.builder().index(idx).build());
                                    }
                                    ChatMessage.ToolCall target = accumToolCalls.get(idx);
                                    if (deltaTc.getId() != null) target.setId(deltaTc.getId());
                                    if (deltaTc.getType() != null) target.setType(deltaTc.getType());
                                    if (deltaTc.getFunction() == null) continue;
                                    ChatMessage.Function fn = target.getFunction();
                                    if (fn == null) {
                                        fn = new ChatMessage.Function();
                                        target.setFunction(fn);
                                    }
                                    if (deltaTc.getFunction().getName() != null) {
                                        fn.setName(deltaTc.getFunction().getName());
                                    }
                                    if (deltaTc.getFunction().getArguments() != null) {
                                        fn.setArguments((fn.getArguments() == null ? "" : fn.getArguments())
                                                + deltaTc.getFunction().getArguments());
                                    }
                                }
                            })
                            .blockLast(timeout);

                    usageAcc.add(roundUsage.get());

                    if (cancelled.get()) {
                        sink.next(new StreamEvent.Cancelled("用户已停止生成"));
                        break;
                    }

                    if (!accumToolCalls.isEmpty()) {
                        working.add(ChatMessage.builder()
                                .role("assistant")
                                .content(assistantContent.length() > 0 ? assistantContent.toString() : null)
                                .toolCalls(accumToolCalls)
                                .build());
                        List<ChatMessage> toolResults = executeToolCalls(accumToolCalls, sessionId, sink);
                        working.addAll(toolResults);
                        continue;
                    }

                    String finalContent = assistantContent.toString();
                    memoryService.saveMessage(sessionId, ChatMessage.assistant(finalContent));
                    sink.next(buildUsageEvent(sessionId, model, usageAcc));
                    log.info("工具调用流式对话完成: session={}, 迭代={}, contentLen={}",
                            sessionId, iter, finalContent.length());
                    sink.complete();
                    completed = true;
                    return;
                }
                if (!completed && iter >= maxIter) {
                    String fallback = "已达到工具调用最大迭代次数(" + maxIter + "),已停止。";
                    memoryService.saveMessage(sessionId, ChatMessage.assistant(fallback));
                    sink.next(new StreamEvent.Token(fallback));
                    sink.next(buildUsageEvent(sessionId, model, usageAcc));
                    sink.complete();
                    completed = true;
                    return;
                }
                if (!completed) {
                    sink.complete();
                }
            } catch (Exception e) {
                log.error("工具调用流式对话异常: session={}", sessionId, e);
                if (!completed) sink.next(new StreamEvent.Error(e.getMessage()));
            } finally {
                if (!completed) sink.complete();
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * 执行一组工具调用;parallel=true 时并发执行,结果按原顺序返回。
     * sink 非空时推送 ToolCall / ToolResult 事件(供前端渲染)。
     */
    private List<ChatMessage> executeToolCalls(List<ChatMessage.ToolCall> toolCalls, String sessionId,
                                               FluxSink<StreamEvent> sink) {
        int n = toolCalls.size();
        String[] names = new String[n];
        String[] args = new String[n];
        String[] ids = new String[n];
        long[] startedAt = new long[n];
        for (int i = 0; i < n; i++) {
            ChatMessage.ToolCall tc = toolCalls.get(i);
            names[i] = tc.getFunction() != null ? tc.getFunction().getName() : "";
            args[i] = (tc.getFunction() != null && tc.getFunction().getArguments() != null)
                    ? tc.getFunction().getArguments() : "{}";
            ids[i] = tc.getId();
            startedAt[i] = System.currentTimeMillis();
            if (sink != null) sink.next(new StreamEvent.ToolCall(names[i], args[i], ids[i], startedAt[i]));
        }

        List<ChatMessage> results = new ArrayList<>();
        boolean parallel = agentProperties.getTools().isParallel() && n > 1;
        if (!parallel) {
            for (int i = 0; i < n; i++) {
                ToolResult r = executeToolCall(names[i], args[i], sessionId);
                emitToolResult(sink, names[i], ids[i], r, startedAt[i]);
                results.add(ChatMessage.toolResult(ids[i], names[i], r.getContent()));
            }
            return results;
        }

        // 并行执行
        List<CompletableFuture<ToolResult>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(CompletableFuture.supplyAsync(
                    () -> executeToolCall(names[idx], args[idx], sessionId), toolExecutor));
        }
        for (int i = 0; i < n; i++) {
            ToolResult r;
            try {
                r = futures.get(i).join();
            } catch (Exception e) {
                r = ToolResult.error("执行异常: " + e.getMessage());
            }
            emitToolResult(sink, names[i], ids[i], r, startedAt[i]);
            results.add(ChatMessage.toolResult(ids[i], names[i], r.getContent()));
        }
        return results;
    }

    private void emitToolResult(FluxSink<StreamEvent> sink, String name, String callId, ToolResult r, long startedAt) {
        if (sink == null) return;
        long finishedAt = System.currentTimeMillis();
        sink.next(new StreamEvent.ToolResult(name, callId, r.getContent(), r.isSuccess(),
                r.getDurationMs(), startedAt, finishedAt));
    }

    /** 执行单个工具调用(测时 + 审计) */
    private ToolResult executeToolCall(String toolName, String argsJson, String sessionId) {
        long start = System.currentTimeMillis();
        Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            ToolResult r = ToolResult.error("未知工具: " + toolName);
            r.setDurationMs(System.currentTimeMillis() - start);
            auditLogService.log(AuditLogService.ACTION_TOOL_CALL, sessionId, toolName,
                    "未知工具", false, r.getDurationMs());
            return r;
        }
        try {
            Map<String, Object> args = StringUtils.hasText(argsJson)
                    ? objectMapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {})
                    : Map.of();
            log.info("调用工具: {} 参数: {}", toolName, argsJson);
            ToolResult result = tool.execute(args);
            result.setDurationMs(System.currentTimeMillis() - start);
            auditLogService.log(AuditLogService.ACTION_TOOL_CALL, sessionId, toolName,
                    "success=" + result.isSuccess() + " args=" + truncate(argsJson, 300),
                    result.isSuccess(), result.getDurationMs());
            return result;
        } catch (Exception e) {
            ToolResult r = ToolResult.error("参数解析失败: " + e.getMessage());
            r.setDurationMs(System.currentTimeMillis() - start);
            auditLogService.log(AuditLogService.ACTION_TOOL_CALL, sessionId, toolName,
                    "参数解析失败: " + e.getMessage(), false, r.getDurationMs());
            return r;
        }
    }

    /** 构造 Usage 事件并落库 + 审计 */
    private StreamEvent.Usage buildUsageEvent(String sessionId, ModelConfig model, DeepSeekResponse.Usage u) {
        int p = u != null ? u.getPromptTokens() : 0;
        int c = u != null ? u.getCompletionTokens() : 0;
        int t = u != null ? u.getTotalTokens() : 0;
        double cost = tokenUsageService.computeCost(model, p, c);
        tokenUsageService.record(sessionId, model.getName(), p, c, t, model);
        auditLogService.log(AuditLogService.ACTION_CHAT, sessionId, null,
                "model=" + model.getName() + " tokens=" + t, true, null);
        return new StreamEvent.Usage(model.getName(), p, c, t, cost, agentProperties.getCost().getCurrency());
    }

    private StreamEvent.Usage buildUsageEvent(String sessionId, ModelConfig model, UsageAcc acc) {
        double cost = tokenUsageService.computeCost(model, acc.prompt, acc.completion);
        tokenUsageService.record(sessionId, model.getName(), acc.prompt, acc.completion, acc.total, model);
        auditLogService.log(AuditLogService.ACTION_CHAT, sessionId, null,
                "model=" + model.getName() + " tokens=" + acc.total, true, null);
        return new StreamEvent.Usage(model.getName(), acc.prompt, acc.completion, acc.total,
                cost, agentProperties.getCost().getCurrency());
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

    /** 加载历史;若超预算则先做记忆摘要压缩 */
    private List<ChatMessage> loadHistoryWithCompression(String sessionId) {
        List<ChatMessage> history = memoryService.getHistory(sessionId);
        AgentProperties.Memory mem = agentProperties.getMemory();
        if (mem.isSummarizeEnabled() && history.size() > mem.getSummarizeThreshold()) {
            summarizeAndTrim(sessionId, history);
            history = memoryService.getHistory(sessionId);
        }
        return history;
    }

    private void summarizeAndTrim(String sessionId, List<ChatMessage> history) {
        int keepRecent = Math.max(2, agentProperties.getMemory().getSummarizeKeepRecent());
        int splitIdx = Math.max(0, history.size() - keepRecent);
        List<ChatMessage> older = history.subList(0, splitIdx);
        if (older.isEmpty()) return;

        String text = older.stream()
                .map(m -> (m.getRole() == null ? "" : m.getRole()) + ": "
                        + (m.getContent() == null ? "[工具调用]" : m.getContent()))
                .collect(Collectors.joining("\n"));
        String oldSummary = memoryService.getSummary(sessionId);
        String prompt = (StringUtils.hasText(oldSummary) ? "已有摘要:\n" + oldSummary + "\n\n" : "")
                + "历史对话:\n" + text;
        List<ChatMessage> msgs = List.of(
                ChatMessage.system("你是对话记忆压缩助手。请把以下历史对话压缩成一段简洁的中文摘要,"
                        + "保留关键事实、用户偏好、结论与未完成事项,不超过 500 字。只输出摘要本身。"),
                ChatMessage.user(prompt));
        try {
            ModelConfig model = modelRouter.defaultConfig();
            DeepSeekResponse resp = deepSeekClient.chat(msgs, null, model)
                    .block(Duration.ofSeconds(model.getTimeout() != null ? model.getTimeout().getSeconds() : 60));
            String summary = resp != null && !CollectionUtils.isEmpty(resp.getChoices())
                    ? resp.getChoices().get(0).getMessage().getContent() : null;
            if (!StringUtils.hasText(summary)) summary = "(历史摘要生成失败)";
            memoryService.saveSummary(sessionId, summary);
            memoryService.trimHistory(sessionId, keepRecent);
            log.info("记忆摘要压缩完成: session={}, 压缩 {} 条 -> {} 字符",
                    sessionId, older.size(), summary.length());
        } catch (Exception e) {
            log.warn("记忆摘要压缩失败,跳过: session={}", sessionId, e);
        }
    }

    /** 构建完整消息列表: system + (摘要) + history + user(含附件) */
    private List<ChatMessage> buildMessages(ChatRequest request, String sessionId,
                                            List<ChatMessage> history, String summary) {
        List<ChatMessage> messages = new ArrayList<>();

        String systemPrompt = StringUtils.hasText(request.getSystemPrompt())
                ? request.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT;

        if (agentProperties.getSkill().isEnabled()) {
            String skillsPrompt = skillService.buildEnabledSkillsPrompt();
            if (StringUtils.hasText(skillsPrompt)) {
                systemPrompt = systemPrompt + skillsPrompt;
            }
        }

        boolean enableRag = request.getEnableRag() == null || request.getEnableRag();
        if (enableRag && agentProperties.getRag().isEnabled()) {
            String ragContext = ragService.retrieveContext(request.getMessage());
            if (StringUtils.hasText(ragContext)) {
                systemPrompt = systemPrompt + "\n\n" + ragContext;
            }
        }

        if (StringUtils.hasText(summary)) {
            systemPrompt = systemPrompt + "\n\n# 历史对话摘要(更早的对话已压缩)\n" + summary;
        }

        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(history);
        messages.add(ChatMessage.user(buildUserMessageWithAttachments(request.getMessage(), request.getAttachments())));
        return messages;
    }

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

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 累计 token 用量(跨多轮工具调用) */
    private static class UsageAcc {
        int prompt;
        int completion;
        int total;

        void add(DeepSeekResponse.Usage u) {
            if (u == null) return;
            prompt += u.getPromptTokens();
            completion += u.getCompletionTokens();
            total += u.getTotalTokens();
        }
    }
}
