package com.foliolens.backend.answer.hcx;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hcx.api")
public record HcxApiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        String appType,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxTokens,
        double temperature,
        double topP) {
}
