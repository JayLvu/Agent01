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

    /** 工具调用配置 */
    private ToolConfig tools = new ToolConfig();

    /** 文件操作工具配置(workspace 沙箱) */
    private FileToolsConfig file = new FileToolsConfig();

    /** Skill 注入配置 */
    private SkillConfig skill = new SkillConfig();

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

    @Data
    public static class ToolConfig {
        /** 是否启用工具调用模式(全局开关) */
        private boolean enabled = true;

        /** 工具调用最大循环次数(防止无限调用) */
        private int maxIterations = 5;

        /** Shell 工具配置 */
        private ShellConfig shell = new ShellConfig();
    }

    @Data
    public static class ShellConfig {
        /** 是否启用 Shell 执行工具 */
        private boolean enabled = true;

        /** 命令执行超时(毫秒) */
        private int timeoutMs = 10000;

        /** 工作目录(为空则用进程当前目录) */
        private String workingDir = "";
    }

    /** 文件操作工具: 所有文件路径被限制在 workspace 沙箱目录下,防止 ../ 越权 */
    @Data
    public static class FileToolsConfig {
        /** 是否启用文件操作工具(列表/读取/写入) */
        private boolean enabled = true;

        /** workspace 沙箱根目录(相对路径以应用工作目录为基准) */
        private String workspaceDir = "./workspace";

        /** 单次读取最大字节数(避免超大文件) */
        private int maxReadBytes = 200 * 1024; // 200KB

        /** 单次写入最大字节数 */
        private int maxWriteBytes = 500 * 1024; // 500KB
    }

    /** Skill 注入配置: 每次对话自动读取已启用 Skill 并拼接到 System Prompt */
    @Data
    public static class SkillConfig {
        /** 是否启用 Skill 注入功能 */
        private boolean enabled = true;

        /** Skill 文件存储目录 */
        private String dir = "./data/skills";
    }
}
