package com.vanzy.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 业务配置: 记忆管理 + RAG 文档检索
 *
 * @author VanzyLiu
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 对话记忆配置 */
    private Memory memory = new Memory();

    /** RAG 文档检索配置 */
    private Rag rag = new Rag();

    @Data
    public static class Memory {
        /** 保留最近 N 轮对话 */
        private int maxHistory = 20;
        /** 对话历史 TTL(小时) */
        private int ttlHours = 24;
    }

    @Data
    public static class Rag {
        /** 是否启用 RAG */
        private boolean enabled = true;
        /** 文档上传目录 */
        private String uploadDir = "./data/docs";
        /** 文档分块大小(字符数) */
        private int chunkSize = 500;
        /** 分块重叠(字符数) */
        private int chunkOverlap = 100;
        /** 检索返回 top-k 文档块 */
        private int topK = 4;
        /** 向量维度(简化实现: 基于 n-gram hash) */
        private int vectorDim = 256;
    }
}
