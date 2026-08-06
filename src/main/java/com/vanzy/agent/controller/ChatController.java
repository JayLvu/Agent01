package com.vanzy.agent.controller;

import com.vanzy.agent.model.ChatRequest;
import com.vanzy.agent.model.ChatResponse;
import com.vanzy.agent.service.ChatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 对话 HTTP API
 *
 * 端点:
 * - POST /chat         同步对话
 * - POST /chat/stream  流式对话(SSE)
 * - DELETE /chat/{sessionId}  清空会话历史
 *
 * @author VanzyLiu
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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
     * 流式对话: 通过 SSE 返回 token 流
     *
     * 示例请求同 /chat,响应为 text/event-stream
     * 每个 event 的 data 字段为一个 token 片段
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody ChatRequest request) {
        request.setStream(true);

        // 预解析 sessionId: 若是首次对话则生成新 ID,通过 session 事件回传给前端
        String sessionId = chatService.resolveSessionId(request.getSessionId());
        request.setSessionId(sessionId);
        log.info("收到流式对话请求: sessionId={}", sessionId);

        // 首个事件: 携带 sessionId,便于前端持久化
        ServerSentEvent<String> sessionEvent = ServerSentEvent.<String>builder()
                .event("session")
                .data(sessionId)
                .build();

        return Flux.concat(
                Flux.just(sessionEvent),
                chatService.chatStream(request)
                        .map(token -> ServerSentEvent.<String>builder()
                                .data(token)
                                .event("token")
                                .build())
        )
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()))
                .onErrorResume(e -> {
                    log.error("流式对话异常", e);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("对话异常: " + e.getMessage())
                            .build());
                });
    }

    /**
     * 清空指定会话的历史
     */
    @DeleteMapping("/{sessionId}")
    public String clearSession(@PathVariable String sessionId) {
        chatService.clearSession(sessionId);
        return "会话已清空: " + sessionId;
    }
}
