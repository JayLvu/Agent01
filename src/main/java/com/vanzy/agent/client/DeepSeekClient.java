package com.vanzy.agent.client;

import com.vanzy.agent.exception.LlmException;
import com.vanzy.agent.model.ChatMessage;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekRequest;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekResponse;
import com.vanzy.agent.model.DeepSeekDtos.DeepSeekStreamChunk;
import com.vanzy.agent.model.ModelConfig;
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
 * DeepSeek API 客户端(OpenAI 兼容格式)
 *
 * 支持:
 * 1. 同步对话(一次性返回)
 * 2. 流式对话(SSE,逐 token 返回,流式携带 usage)
 * 3. 多模型路由(每次调用按 {@link ModelConfig} 动态指定 baseUrl / apiKey / 模型名)
 *
 * API 文档: https://api-docs.deepseek.com
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class DeepSeekClient {

    private static final String CHAT_PATH = "/chat/completions";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(WebClient deepSeekWebClient, ObjectMapper objectMapper) {
        this.webClient = deepSeekWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步对话(可选工具),返回完整响应
     */
    public Mono<DeepSeekResponse> chat(List<ChatMessage> messages, List<Map<String, Object>> tools, ModelConfig model) {
        DeepSeekRequest request = buildRequest(messages, false, model);
        if (tools != null && !tools.isEmpty()) {
            request.setTools(tools);
            request.setToolChoice("auto");
        }
        log.debug("调用 DeepSeek 同步对话: model={}, 消息数={}, 工具数={}",
                model.getName(), messages.size(), tools == null ? 0 : tools.size());

        return webClient.post()
                .uri(model.getBaseUrl() + CHAT_PATH)
                .header("Authorization", "Bearer " + model.getApiKey())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DeepSeekResponse.class)
                .doOnError(e -> log.error("DeepSeek 调用失败: model={}", model.getName(), e))
                .onErrorMap(e -> new LlmException("DeepSeek 调用失败: " + e.getMessage(), e));
    }

    /**
     * 流式对话(SSE,可选工具),返回 token 流;最后一个 chunk 携带 usage。
     */
    public Flux<DeepSeekStreamChunk> chatStream(List<ChatMessage> messages, List<Map<String, Object>> tools, ModelConfig model) {
        DeepSeekRequest request = buildRequest(messages, true, model);
        if (tools != null && !tools.isEmpty()) {
            request.setTools(tools);
            request.setToolChoice("auto");
        }
        log.debug("调用 DeepSeek 流式对话: model={}, 消息数={}, 工具数={}",
                model.getName(), messages.size(), tools == null ? 0 : tools.size());

        return webClient.post()
                .uri(model.getBaseUrl() + CHAT_PATH)
                .header("Authorization", "Bearer " + model.getApiKey())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .<String>handle((sse, sink) -> {
                    String data = sse.data();
                    if (data == null) return;
                    String trimmed = data.trim();
                    if (trimmed.isEmpty() || "[DONE]".equals(trimmed)) return;
                    sink.next(data);
                })
                .<DeepSeekStreamChunk>handle((data, sink) -> {
                    try {
                        sink.next(objectMapper.readValue(data, DeepSeekStreamChunk.class));
                    } catch (Exception e) {
                        log.debug("解析 SSE 数据块失败,跳过(可能为 comment/心跳帧): dataPrefix={}",
                                data.length() > 120 ? data.substring(0, 120) + "..." : data);
                    }
                })
                .doOnError(e -> log.error("DeepSeek 流式调用失败: model={}", model.getName(), e))
                .onErrorMap(e -> new LlmException("DeepSeek 流式调用失败: " + e.getMessage(), e));
    }

    private DeepSeekRequest buildRequest(List<ChatMessage> messages, boolean stream, ModelConfig model) {
        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel(model.getName());
        request.setMessages(messages);
        request.setTemperature(model.getTemperature());
        request.setMaxTokens(model.getMaxTokens());
        request.setStream(stream);
        if (stream) {
            // 让上游在最后一个 chunk 返回 usage,用于 token 统计
            request.setStreamOptions(Map.of("include_usage", true));
        }
        return request;
    }
}
