package com.digitalmall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型导购配置（OpenAI 兼容接口）
 * api-key 未填写（或仍为占位符）时，导购自动降级为本地检索 + 模板回答模式
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";

    private String apiKey = "your-api-key";

    private String model = "doubao-pro-32k";

    private String embeddingModel = "doubao-embedding";

    private Double temperature = 0.3;

    /** 是否启用真实大模型（api-key 为空/占位符则关闭） */
    public boolean available() {
        return apiKey != null
                && !apiKey.isBlank()
                && !"your-api-key".equalsIgnoreCase(apiKey.trim());
    }
}
