package com.vanzy.agent.client;

import com.vanzy.agent.config.DeepSeekProperties;
import com.vanzy.agent.exception.LlmException;
import com.vanzy.agent.model.ChatMessage;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekRequest;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekResponse;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekStreamChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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
    private final ObjectMapper objectMapper;

    public DeepSeekClient(WebClient deepSeekWebClient, DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.webClient = deepSeekWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步对话: 一次性返回完整回复
     *
     * @param messages 对话消息列表(含 system + history + 当前用户消息)
     * @return DeepSeek 完整响应
     */
    public Mono<DeepSeekResponse> chat(List<ChatMessage> messages) {
        return chat(messages, null);
    }

    /**
     * 同步对话(支持工具调用): 一次性返回完整回复
     *
     * @param messages 对话消息列表
     * @param tools    工具 schema 列表(为 null 则不带 tools)
     * @return DeepSeek 完整响应
     */
    public Mono<DeepSeekResponse> chat(List<ChatMessage> messages, List<Map<String, Object>> tools) {
        DeepSeekRequest request = buildRequest(messages, false);
        if (tools != null && !tools.isEmpty()) {
            request.setTools(tools);
            request.setToolChoice("auto");
        }
        log.debug("调用 DeepSeek 同步对话, 消息数: {}, 是否带工具: {}", messages.size(), tools != null);

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
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(data -> data != null && !"[DONE]".equals(data.trim()))
                .<DeepSeekStreamChunk>handle((data, sink) -> {
                    try {
                        sink.next(objectMapper.readValue(data, DeepSeekStreamChunk.class));
                    } catch (Exception e) {
                        log.warn("解析 SSE 数据块失败,跳过: data={}", data);
                    }
                })
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
