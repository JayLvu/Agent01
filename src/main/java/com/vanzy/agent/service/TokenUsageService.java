package com.vanzy.agent.service;

import com.vanzy.agent.config.AgentProperties;
import com.vanzy.agent.model.ModelConfig;
import com.vanzy.agent.persistence.TokenUsageRecord;
import com.vanzy.agent.persistence.TokenUsageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Token 使用统计服务: 记录每次调用的 token 量与成本,并提供汇总。
 *
 * @author VanzyLiu
 */
@Slf4j
@Service
public class TokenUsageService {

    private final TokenUsageRepository repository;
    private final AgentProperties properties;

    public TokenUsageService(TokenUsageRepository repository, AgentProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 计算一次调用的成本(未启用成本统计返回 0)
     */
    public double computeCost(ModelConfig modelConfig, int promptTokens, int completionTokens) {
        if (!properties.getCost().isEnabled()) {
            return 0;
        }
        double promptPrice = modelConfig != null && modelConfig.getPromptPrice() > 0
                ? modelConfig.getPromptPrice() : properties.getCost().getPromptPrice();
        double completionPrice = modelConfig != null && modelConfig.getCompletionPrice() > 0
                ? modelConfig.getCompletionPrice() : properties.getCost().getCompletionPrice();
        return (promptTokens * promptPrice + completionTokens * completionPrice) / 1_000_000.0;
    }

    /**
     * 记录一次 LLM 调用(若未启用成本统计,则 cost 记 0)
     */
    public void record(String sessionId, String model, int promptTokens, int completionTokens, int totalTokens, ModelConfig modelConfig) {
        if (totalTokens <= 0 && promptTokens <= 0 && completionTokens <= 0) {
            return;
        }
        double cost = computeCost(modelConfig, promptTokens, completionTokens);
        try {
            repository.save(TokenUsageRecord.builder()
                    .sessionId(sessionId)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .cost(cost)
                    .currency(properties.getCost().getCurrency())
                    .createdAt(java.time.LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Token 统计写入失败: {}", e.getMessage());
        }
    }

    public List<TokenUsageRecord> recent() {
        return repository.findTop200ByOrderByIdDesc();
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalTokens", nvl(repository.sumTotalTokens()));
        m.put("promptTokens", nvl(repository.sumPromptTokens()));
        m.put("completionTokens", nvl(repository.sumCompletionTokens()));
        m.put("totalCost", nvl(repository.sumCost()));
        m.put("currency", properties.getCost().getCurrency());
        m.put("calls", repository.count());
        return m;
    }

    private static long nvl(Long v) {
        return v == null ? 0L : v;
    }

    private static double nvl(Double v) {
        return v == null ? 0.0 : v;
    }
}
