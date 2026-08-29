package com.vanzy.agent.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Token 使用统计实体: 每次 LLM 调用的 token 量与成本
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "token_usage", indexes = {
        @Index(name = "idx_usage_session", columnList = "sessionId"),
        @Index(name = "idx_usage_created", columnList = "createdAt")
})
public class TokenUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会话 ID */
    @Column(length = 64)
    private String sessionId;

    /** 模型名 */
    @Column(length = 64)
    private String model;

    /** 输入 token 数 */
    private int promptTokens;

    /** 输出 token 数 */
    private int completionTokens;

    /** 总 token 数 */
    private int totalTokens;

    /** 本次调用成本 */
    private double cost;

    /** 货币单位 */
    @Column(length = 8)
    private String currency;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
