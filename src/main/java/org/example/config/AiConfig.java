package org.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 从 application.yml 的 ai.* 绑定默认 AI 端点、Key、模型等，可被系统配置表覆盖。 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    /**
     * OpenAI/通义千问 兼容接口地址
     */
    private String endpoint;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 最大 tokens
     */
    private Integer maxTokens;
}

