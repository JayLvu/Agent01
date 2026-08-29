package com.vanzy.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanzy.agent.model.AttachmentFile;
import com.vanzy.agent.model.ChatRequest;
import com.vanzy.agent.model.ChatResponse;
import com.vanzy.agent.model.StreamEvent;
import com.vanzy.agent.service.AttachmentParser;
import com.vanzy.agent.service.AuditLogService;
import com.vanzy.agent.service.CancellationRegistry;
import com.vanzy.agent.service.ChatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;

/**
 * 对话 HTTP API
 *
 * 端点:
 * - POST /chat         同步对话
 * - POST /chat/stream  流式对话(SSE,支持工具调用事件)
 * - DELETE /chat/{sessionId}  清空会话历史
 *
 * @author VanzyLiu
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final AttachmentParser attachmentParser;
    private final CancellationRegistry cancellationRegistry;
    private final AuditLogService auditLogService;

    public ChatController(ChatService chatService, ObjectMapper objectMapper, AttachmentParser attachmentParser,
                          CancellationRegistry cancellationRegistry, AuditLogService auditLogService) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.attachmentParser = attachmentParser;
        this.cancellationRegistry = cancellationRegistry;
        this.auditLogService = auditLogService;
    }

    /**
     * 附件上传: 后端解析 Word/Excel/PDF/PPT/TXT/MD 等文件,提取纯文本返回给前端
     * 前端拿到文本后,作为 AttachmentFile 放入对话请求的 attachments 字段
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentFile upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        log.info("收到附件上传: fileName={}, size={}", fileName, file.getSize());
        try {
            String content = attachmentParser.parse(fileName, file.getBytes());
            log.info("附件解析完成: fileName={}, 提取字符数={}", fileName, content.length());
            return AttachmentFile.builder()
                    .fileName(fileName)
                    .fileType(AttachmentParser.detectExt(fileName))
                    .fileSize(file.getSize())
                    .content(content)
                    .build();
        } catch (Exception e) {
            log.error("附件解析失败: fileName={}", fileName, e);
            throw new IllegalArgumentException("附件解析失败: " + e.getMessage());
        }
    }

    /**
     * 同步对话: 一次性返回完整回复
     *
     * 示例请求:
     * POST /api/v1/chat
     * {
     *   "sessionId": "可选,不传则新建",
     *   "message": "你好,介绍一下自己",
     *   "enableRag": true
     * }
     */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        log.info("收到对话请求: sessionId={}, messageLen={}",
                request.getSessionId(),
                request.getMessage() != null ? request.getMessage().length() : 0);
        return chatService.chat(request);
    }

    /**
     * 流式对话: 通过 SSE 返回 token 流(使用 SseEmitter 保证每次事件立即 flush,
     * 解决 Spring MVC Servlet 栈下返回 Flux<ServerSentEvent> 被缓冲的问题)
     *
     * 示例请求同 /chat,响应为 text/event-stream
     * 每个 event 的 data 字段为一个 token 片段
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        request.setStream(true);

        // 预解析 sessionId: 若是首次对话则生成新 ID,通过 session 事件回传给前端
        String sessionId = chatService.resolveSessionId(request.getSessionId());
        request.setSessionId(sessionId);

        // 生成 requestId 并登记取消信号
        String requestId = request.getRequestId() != null && !request.getRequestId().isBlank()
                ? request.getRequestId() : UUID.randomUUID().toString().replace("-", "");
        request.setRequestId(requestId);
        Sinks.Many<String> cancelSink = cancellationRegistry.register(requestId);
        log.info("收到流式对话请求: sessionId={}, requestId={}", sessionId, requestId);

        // 0 表示不超时(由 LLM 调用超时 + Flux.create 内部 blockLast 兜底); 必须设置否则 Tomcat 默认 30s 会断开
        SseEmitter emitter = new SseEmitter(0L);

        // 异步订阅: 避免阻塞 Tomcat 请求线程; SseEmitter.send 线程安全,会立即 flush 到 socket
        Thread worker = new Thread(() -> {
            try {
                // 1) 首个事件: sessionId + requestId
                emitter.send(SseEmitter.event()
                        .name("session")
                        .data(toJson(java.util.Map.of("sessionId", sessionId, "requestId", requestId)), MediaType.APPLICATION_JSON));

                // 2) 订阅业务流: 逐事件推送(Token / ToolCall / ToolResult / Usage / Cancelled / Error)
                chatService.chatStream(request, cancelSink.asFlux())
                        .doOnNext(ev -> {
                            try {
                                switch (ev) {
                                    case StreamEvent.Token t -> emitter.send(SseEmitter.event()
                                            .name("token").data(t.content(), MediaType.TEXT_PLAIN));
                                    case StreamEvent.ToolCall tc -> emitter.send(SseEmitter.event()
                                            .name("tool_call").data(toJson(tc), MediaType.APPLICATION_JSON));
                                    case StreamEvent.ToolResult tr -> emitter.send(SseEmitter.event()
                                            .name("tool_result").data(toJson(tr), MediaType.APPLICATION_JSON));
                                    case StreamEvent.Usage u -> emitter.send(SseEmitter.event()
                                            .name("usage").data(toJson(u), MediaType.APPLICATION_JSON));
                                    case StreamEvent.Cancelled c -> emitter.send(SseEmitter.event()
                                            .name("cancelled").data(c.reason(), MediaType.TEXT_PLAIN));
                                    case StreamEvent.Error err -> emitter.send(SseEmitter.event()
                                            .name("error").data(err.message(), MediaType.TEXT_PLAIN));
                                }
                            } catch (Exception sendEx) {
                                throw new RuntimeException("SSE send failed", sendEx);
                            }
                        })
                        .doOnError(e -> log.error("流式对话业务流异常: sessionId={}", sessionId, e))
                        .onErrorResume(e -> reactor.core.publisher.Flux.just(
                                new StreamEvent.Error("对话异常: " + e.getMessage())))
                        .blockLast(); // 阻塞直到业务流完成

                // 3) 结束事件
                emitter.send(SseEmitter.event().name("done").data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
                log.info("流式对话正常结束: sessionId={}, requestId={}", sessionId, requestId);
            } catch (Throwable e) {
                log.error("流式对话线程异常: sessionId={}", sessionId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                            "对话异常: " + e.getMessage(), MediaType.TEXT_PLAIN));
                } catch (Exception ignore) { /* ignore */ }
                emitter.completeWithError(e);
            } finally {
                cancellationRegistry.remove(requestId);
            }
        }, "sse-stream-" + requestId.substring(0, Math.min(8, requestId.length())));
        worker.setDaemon(true);
        worker.start();

        // 客户端断开时: 触发取消,让底层 LLM 请求真正中断
        emitter.onTimeout(() -> {
            log.warn("流式对话超时: sessionId={}", sessionId);
            cancellationRegistry.cancel(requestId);
        });
        emitter.onError(t -> {
            log.warn("流式对话连接断开,触发取消: sessionId={}, err={}", sessionId, t.getMessage());
            cancellationRegistry.cancel(requestId);
        });
        emitter.onCompletion(() -> log.debug("流式对话 SseEmitter complete: sessionId={}", sessionId));

        return emitter;
    }

    /**
     * 停止生成: 前端显式调用以真正取消进行中的流式请求
     */
    @PostMapping("/cancel/{requestId}")
    public Map<String, Object> cancel(@PathVariable String requestId) {
        cancellationRegistry.cancel(requestId);
        auditLogService.log(AuditLogService.ACTION_CANCEL, null, null, "requestId=" + requestId, true, null);
        return Map.of("ok", true, "requestId", requestId);
    }

    /**
     * 清空指定会话的历史
     */
    @DeleteMapping("/{sessionId}")
    public String clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        return "会话已清空: " + sessionId;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
