package com.vanzy.agent.client;

import com.vanzy.agent.config.DeepSeekProperties;
import com.vanzy.agent.exception.LlmException;
import com.vanzy.agent.model.ChatMessage;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekRequest;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekResponse;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekStreamChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * DeepSeek API 客户端
 *
 * 支持两种调用模式:
 * 1. 同步对话: 一次性返回完整回复
 * 2. 流式对话(SSE): 通过 Server-Sent Events 逐 token 返回
 *
 * API 兼容 OpenAI 格式,文档: https://api-docs.deepseek.com
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class DeepSeekClient {

    private static final String CHAT_PATH = "/v1/chat/completions";

    private final WebClient webClient;
    private final DeepSeekProperties properties;

    public DeepSeekClient(WebClient deepSeekWebClient, DeepSeekProperties properties) {
        this.webClient = deepSeekWebClient;
        this.properties = properties;
    }

    /**
     * 同步对话: 一次性返回完整回复
     *
     * @param messages 对话消息列表(含 system + history + 当前用户消息)
     * @return DeepSeek 完整响应
     */
    public Mono<DeepSeekResponse> chat(List<ChatMessage> messages) {
        DeepSeekRequest request = buildRequest(messages, false);
        log.debug("调用 DeepSeek 同步对话, 消息数: {}", messages.size());

        return webClient.post()
                .uri(CHAT_PATH)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DeepSeekResponse.class)
                .doOnError(e -> log.error("DeepSeek 调用失败", e))
                .onErrorMap(e -> new LlmException("DeepSeek 调用失败: " + e.getMessage(), e));
    }

    /**
     * 流式对话(SSE): 返回 token 流
     *
     * @param messages 对话消息列表
     * @return DeepSeek 流式响应块 Flux
     */
    public Flux<DeepSeekStreamChunk> chatStream(List<ChatMessage> messages) {
        DeepSeekRequest request = buildRequest(messages, true);
        log.debug("调用 DeepSeek 流式对话, 消息数: {}", messages.size());

        return webClient.post()
                .uri(CHAT_PATH)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(DeepSeekStreamChunk.class)
                .doOnError(e -> log.error("DeepSeek 流式调用失败", e))
                .onErrorMap(e -> new LlmException("DeepSeek 流式调用失败: " + e.getMessage(), e));
    }

    private DeepSeekRequest buildRequest(List<ChatMessage> messages, boolean stream) {
        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel(properties.getModel());
        request.setMessages(messages);
        request.setTemperature(properties.getTemperature());
        request.setMaxTokens(properties.getMaxTokens());
        request.setStream(stream);
        return request;
    }
}
