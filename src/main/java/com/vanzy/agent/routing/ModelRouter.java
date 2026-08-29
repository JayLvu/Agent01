package com.vanzy.agent.routing;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.config.DeepSeekProperties;
import com.vanzy.agent.model.ChatRequest;
import com.vanzy.agent.model.ModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 多模型路由器:
 * 1. 请求显式指定 model → 直接用该模型名(复用默认接入点)
 * 2. 开启 reasoning 或命中推理关键词 → 用 reasoning 模型
 * 3. 否则用默认模型
 *
 * 说明: 当前不同模型共用同一 baseUrl/apiKey(DeepSeek 官方均走同一接入点),
 * 仅切换 model 名与采样参数;若要接入异构模型(不同厂商),可在 DeepSeekProperties
 * 扩展 per-model 接入点,本类仅负责"选名字"。
 *
 * @author VanzyLiu
 */
@Slf4j
@Component
public class ModelRouter {

    private final DeepSeekProperties deepSeek;
    private final AgentProperties properties;

    public ModelRouter(DeepSeekProperties deepSeek, AgentProperties properties) {
        this.deepSeek = deepSeek;
        this.properties = properties;
    }

    /**
     * 根据请求解析本次调用应使用的模型配置
     */
    public ModelConfig resolve(ChatRequest request) {
        AgentProperties.Router router = properties.getRouter();

        String modelName = deepSeek.getModel();
        boolean reasoner = false;

        // 1. 请求显式指定模型
        if (StringUtils.hasText(request.getModel())) {
            modelName = request.getModel().trim();
        } else if (router.isEnabled()) {
            // 2. 深度推理标记或关键词
            if (Boolean.TRUE.equals(request.getReasoning())) {
                if (StringUtils.hasText(router.getReasoningModel())) {
                    modelName = router.getReasoningModel();
                    reasoner = true;
                }
            } else if (StringUtils.hasText(router.getReasoningModel()) && matchesReasoningKeywords(request.getMessage())) {
                modelName = router.getReasoningModel();
                reasoner = true;
            } else if (StringUtils.hasText(router.getDefaultModel())) {
                modelName = router.getDefaultModel();
            }
        }

        ModelConfig config = ModelConfig.builder()
                .name(modelName)
                .baseUrl(deepSeek.getBaseUrl())
                .apiKey(deepSeek.getApiKey())
                .temperature(reasoner ? 0.1 : deepSeek.getTemperature())
                .maxTokens(deepSeek.getMaxTokens())
                .timeout(deepSeek.getTimeout())
                .reasoner(reasoner)
                .promptPrice(properties.getCost().getPromptPrice())
                .completionPrice(properties.getCost().getCompletionPrice())
                .build();

        log.debug("模型路由: requestModel={}, reasoning={}, resolved={}",
                request.getModel(), request.getReasoning(), modelName);
        return config;
    }

    /** 返回默认模型配置(供记忆摘要等内部调用使用) */
    public ModelConfig defaultConfig() {
        ModelConfig config = ModelConfig.builder()
                .name(deepSeek.getModel())
                .baseUrl(deepSeek.getBaseUrl())
                .apiKey(deepSeek.getApiKey())
                .temperature(deepSeek.getTemperature())
                .maxTokens(deepSeek.getMaxTokens())
                .timeout(deepSeek.getTimeout())
                .reasoner(false)
                .promptPrice(properties.getCost().getPromptPrice())
                .completionPrice(properties.getCost().getCompletionPrice())
                .build();
        return config;
    }

    private boolean matchesReasoningKeywords(String message) {
        if (!StringUtils.hasText(message)) return false;
        String keywords = properties.getRouter().getReasoningKeywords();
        if (!StringUtils.hasText(keywords)) return false;
        List<String> kws = Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return kws.stream().anyMatch(message::contains);
    }
}
