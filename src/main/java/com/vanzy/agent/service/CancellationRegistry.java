package com.vanzy.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 取消信号注册中心:
 * 每个流式请求以 requestId 登记一个可多播的取消信号,停止生成时向其发送信号,
 * 使正在进行的 LLM 流式调用被 takeUntilOther 真正取消(关闭底层 HTTP 连接)。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class CancellationRegistry {

    private final ConcurrentHashMap<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    /** 登记一个请求,返回其取消信号 Flux 的 source */
    public Sinks.Many<String> register(String requestId) {
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        sinks.put(requestId, sink);
        return sink;
    }

    /** 触发取消 */
    public void cancel(String requestId) {
        Sinks.Many<String> sink = sinks.get(requestId);
        if (sink != null) {
            sink.tryEmitNext("cancel");
            log.info("已发出取消信号: requestId={}", requestId);
        }
    }

    /** 移除登记 */
    public void remove(String requestId) {
        Sinks.Many<String> sink = sinks.remove(requestId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
}
