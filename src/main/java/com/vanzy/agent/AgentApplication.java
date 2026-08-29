package com.vanzy.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 对话 Agent 启动类
 *
 * 功能特性:
 * 1. 接入 DeepSeek 大模型,支持同步与 SSE 流式对话
 * 2. 基于 Redis 的多轮对话记忆管理
 * 3. RAG 文档检索增强(支持 PDF/Word/TXT 上传与向量化)
 * 4. 标准 RESTful HTTP API
 *
 * @author VanzyLiu
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
