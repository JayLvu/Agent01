package com.vanzy.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 业务配置: 记忆管理 + RAG 文档检索 + 多模型路由 + 成本统计 + 工具配置
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

    /** 多模型路由配置 */
    private Router router = new Router();

    /** Token 成本统计配置 */
    private Cost cost = new Cost();

    @Data
    public static class Memory {
        /** 保留最近 N 轮对话 */
        private int maxHistory = 20;
        /** 对话历史 TTL(小时) */
        private int ttlHours = 24;
        /** 是否启用记忆摘要压缩 */
        private boolean summarizeEnabled = true;
        /** 历史消息数超过该阈值时触发摘要 */
        private int summarizeThreshold = 16;
        /** 摘要后保留最近 N 条消息 */
        private int summarizeKeepRecent = 8;
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
    public static class Router {
        /** 是否启用多模型路由 */
        private boolean enabled = true;
        /** 默认模型(留空则用 deepseek.model) */
        private String defaultModel = "";
        /** 深度推理模型(留空则用默认) */
        private String reasoningModel = "";
        /** 命中这些关键词则路由到 reasoning 模型(逗号分隔) */
        private String reasoningKeywords = "分析,推理,为什么,对比,总结,设计,方案,debug,优化,实现";
    }

    @Data
    public static class Cost {
        /** 是否启用成本统计 */
        private boolean enabled = true;
        /** 货币单位 */
        private String currency = "CNY";
        /** 输入每百万 token 价格 */
        private double promptPrice = 2.0;
        /** 输出每百万 token 价格 */
        private double completionPrice = 8.0;
    }

    @Data
    public static class ToolConfig {
        /** 是否启用工具调用模式(全局开关) */
        private boolean enabled = true;

        /** 工具调用最大循环次数(防止无限调用) */
        private int maxIterations = 8;

        /** 是否并行执行无依赖的工具调用 */
        private boolean parallel = true;

        /** Shell 工具配置 */
        private ShellConfig shell = new ShellConfig();

        /** 联网搜索工具配置 */
        private SearchConfig search = new SearchConfig();

        /** 浏览器自动化工具配置 */
        private BrowserConfig browser = new BrowserConfig();

        /** 定时任务工具配置 */
        private SchedulerConfig scheduler = new SchedulerConfig();
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

    @Data
    public static class SearchConfig {
        /** 是否启用联网搜索工具 */
        private boolean enabled = true;
        /** 搜索 provider: bing(默认,无需 key) | duckduckgo | serper(需 api-key) */
        private String provider = "bing";
        /** provider 需要的 API Key(如 serper) */
        private String apiKey = "";
        /** 返回结果条数上限 */
        private int maxResults = 5;
    }

    @Data
    public static class BrowserConfig {
        /** 是否启用浏览器自动化(browse_url)工具 */
        private boolean enabled = true;
        /** 抓取超时(毫秒) */
        private int timeoutMs = 10000;
        /** 提取正文最大字符数 */
        private int maxChars = 6000;
    }

    @Data
    public static class SchedulerConfig {
        /** 是否启用定时任务工具 */
        private boolean enabled = true;
        /** 调度器轮询间隔(毫秒) */
        private long tickMs = 60000;
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
