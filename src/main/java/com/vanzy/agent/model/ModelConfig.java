package com.vanzy.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * 单个模型的运行时配置: 名称、接入点、密钥与采样参数。
 * 由 DeepSeekProperties(默认) 或 AgentProperties.Router(路由) 派生。
 *
 * @author VanzyLiu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfig {

    /** 模型名(如 deepseek-chat / deepseek-reasoner) */
    private String name;

    /** API 基础地址 */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 采样温度 */
    private Double temperature;

    /** 单次最大输出 token */
    private Integer maxTokens;

    /** 请求超时 */
    private Duration timeout;

    /** 是否为深度推理模型 */
    private boolean reasoner;

    /** 输入每百万 token 价格 */
    private double promptPrice;

    /** 输出每百万 token 价格 */
    private double completionPrice;
}
