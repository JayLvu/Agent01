package com.vanzy.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话响应 DTO
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 会话 ID */
    private String sessionId;

    /** 本次回复内容 */
    private String content;

    /** 引用的 RAG 文档片段(无则为空) */
    private List<DocumentChunk> references;

    /** Token 使用统计 */
    private Usage usage;

    /** 模型名称 */
    private String model;

    /** 响应时间(ms) */
    private Long durationMs;

    /** 时间戳 */
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
