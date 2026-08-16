package com.stockit.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.internal-api")
public record InternalApiProperties(
        String key,
        Long userId,
        String principalName
) {

    public boolean isConfigured() {
        return key != null && !key.isBlank()
                && userId != null && userId > 0
                && principalName != null && !principalName.isBlank();
    }
}
