package com.stockit.backend.feature.demandforecast.orchestration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demand-forecast.orchestration")
public record DemandForecastOrchestrationProperties(
        boolean enabled,
        String fastApiBaseUrl,
        String fastApiKey,
        String submitPath,
        String statusPath,
        String dailyImportPath,
        String importPath,
        Duration connectTimeout,
        Duration readTimeout,
        Duration jobTimeout
) {
    public String requiredBaseUrl() {
        if (fastApiBaseUrl == null || fastApiBaseUrl.isBlank()) {
            throw new IllegalStateException("DEMAND_FORECAST_FASTAPI_BASE_URL is required");
        }
        return fastApiBaseUrl;
    }

    public String resolvedSubmitPath() {
        return submitPath == null || submitPath.isBlank()
                ? "/api/v1/demand-forecasts/jobs"
                : submitPath;
    }

    public String resolvedStatusPath() {
        return statusPath == null || statusPath.isBlank()
                ? "/api/v1/demand-forecasts/jobs/{azureJobId}"
                : statusPath;
    }

    public String resolvedImportPath() {
        return importPath == null || importPath.isBlank()
                ? "/api/v1/demand-forecasts/jobs/{azureJobId}/import"
                : importPath;
    }

    public String resolvedDailyImportPath() {
        return dailyImportPath == null || dailyImportPath.isBlank()
                ? "/api/v1/demand-forecasts/jobs/{azureJobId}/daily-import"
                : dailyImportPath;
    }

    public Duration resolvedConnectTimeout() {
        return connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
    }

    public Duration resolvedReadTimeout() {
        return readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    public Duration resolvedJobTimeout() {
        return jobTimeout == null ? Duration.ofHours(2) : jobTimeout;
    }
}
