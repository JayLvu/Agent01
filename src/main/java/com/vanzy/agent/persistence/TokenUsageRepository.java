package com.vanzy.agent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Token 使用统计仓库
 *
 * @author VanzyLiu
 */
public interface TokenUsageRepository extends JpaRepository<TokenUsageRecord, Long> {

    List<TokenUsageRecord> findTop200ByOrderByIdDesc();

    @Query("select sum(t.totalTokens) from TokenUsageRecord t")
    Long sumTotalTokens();

    @Query("select sum(t.promptTokens) from TokenUsageRecord t")
    Long sumPromptTokens();

    @Query("select sum(t.completionTokens) from TokenUsageRecord t")
    Long sumCompletionTokens();

    @Query("select sum(t.cost) from TokenUsageRecord t")
    Double sumCost();
}
