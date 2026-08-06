package com.vanzy.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * DeepSeek API 配置属性
 *
 * @author VanzyLiu
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** API Key(从 https://platform.deepseek.com 获取) */
    private String apiKey;

    /** API 基础地址 */
    private String baseUrl = "https://api.deepseek.com";

    /** 默认模型: deepseek-chat(对话) / deepseek-coder(代码) */
    private String model = "deepseek-chat";

    /** 温度参数: 0-2,值越大回答越发散 */
    private Double temperature = 0.7;

    /** 单次回复最大 token 数 */
    private Integer maxTokens = 2048;

    /** 请求超时时间 */
    private Duration timeout = Duration.ofSeconds(60);
}
