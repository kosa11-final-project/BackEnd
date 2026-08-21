package com.stockit.backend.feature.strategy.forecast;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.stockit.backend.config.InternalApiProperties;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;

/**
 * 내부 API Key 인증을 적용해 ML 일별 수요예측을 호출하는 REST Adapter
 *
 * <p>HTTP 상태와 공급자 오류 코드를 재시도 가능 여부로 변환하고 인증 정보나
 * 전체 응답 원문은 로그에 남기지 않음</p>
 */
@Component
public class MlForecastProvider implements ForecastProvider {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final StrategyGenerationStage STAGE =
            StrategyGenerationStage.FORECASTING;

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final StrategyForecastProperties properties;
    private final InternalApiProperties internalApiProperties;

    public MlForecastProvider(
            @Qualifier("strategyForecastRestClientBuilder")
            RestClient.Builder strategyForecastRestClientBuilder,
            ObjectMapper objectMapper,
            StrategyForecastProperties properties,
            InternalApiProperties internalApiProperties
    ) {
        this.restClientBuilder = strategyForecastRestClientBuilder;
        this.objectMapper = objectMapper.copy().disable(
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
        );
        this.properties = properties;
        this.internalApiProperties = internalApiProperties;
    }

    /**
     * Case에서 확정된 예측 요청을 인증 헤더와 함께 전송하고 성공 응답 역직렬화
     */
    @Override
    public StrategyForecastResponse forecast(StrategyForecastRequest request) {
        validateConfiguration();
        RestClient client = restClientBuilder.clone()
                .baseUrl(normalizedBaseUrl())
                .build();
        try {
            return client.post()
                    .uri(properties.getPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    // 내부 서비스 인증 키를 URL이나 본문에 포함하지 않고 전용 헤더로 전달
                    .header(API_KEY_HEADER, internalApiProperties.key())
                    .body(request)
                    .exchange((httpRequest, response) -> parseResponse(
                            response.getStatusCode(),
                            response.getBody().readAllBytes()
                    ));
        } catch (PermanentStrategyGenerationException
                 | RetryableStrategyGenerationException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (containsCause(exception, SocketTimeoutException.class)
                    || containsCause(exception, HttpTimeoutException.class)) {
                throw retryable(
                        "FORECAST_API_TIMEOUT",
                        "Demand forecast API timed out",
                        exception
                );
            }
            throw retryable(
                    "FORECAST_API_UNAVAILABLE",
                    "Demand forecast API connection failed",
                    exception
            );
        } catch (RuntimeException exception) {
            throw retryable(
                    "FORECAST_API_UNAVAILABLE",
                    "Unexpected demand forecast API client failure",
                    exception
            );
        }
    }

    private StrategyForecastResponse parseResponse(
            HttpStatusCode status,
            byte[] responseBody
    ) {
        if (status.is2xxSuccessful()) {
            try {
                return objectMapper.readValue(responseBody, StrategyForecastResponse.class);
            } catch (IOException exception) {
                throw permanent(
                        "FORECAST_RESPONSE_INVALID",
                        "Demand forecast success response is invalid JSON",
                        exception
                );
            }
        }

        StrategyForecastApiError error = parseError(responseBody);
        String providerCode = error == null ? null : error.code();
        String providerMessage = error == null || error.message() == null
                ? "Demand forecast API rejected the request"
                : error.message();

        if (status.value() == 408 || status.value() == 429 || status.is5xxServerError()) {
            String code = status.value() == 429
                    ? "FORECAST_API_RATE_LIMITED"
                    : "FORECAST_API_UNAVAILABLE";
            throw retryable(code, providerMessage, null);
        }
        if ("FORECAST_NOT_READY".equals(providerCode)) {
            throw retryable(providerCode, providerMessage, null);
        }
        if (status.value() == 401 || status.value() == 403) {
            throw permanent(
                    "FORECAST_API_AUTH_FAILED",
                    "Demand forecast API authentication failed",
                    null
            );
        }
        if ("FORECAST_UNAVAILABLE".equals(providerCode)) {
            throw permanent(providerCode, providerMessage, null);
        }
        throw permanent(
                "FORECAST_API_REQUEST_REJECTED",
                providerCode == null
                        ? providerMessage
                        : "[" + providerCode + "] " + providerMessage,
                null
        );
    }

    private StrategyForecastApiError parseError(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, StrategyForecastApiError.class);
        } catch (IOException exception) {
            return null;
        }
    }

    private void validateConfiguration() {
        if (properties.getBaseUrl() == null
                || properties.getBaseUrl().isBlank()
                || properties.getPath() == null
                || properties.getPath().isBlank()
                || internalApiProperties.key() == null
                || internalApiProperties.key().isBlank()) {
            throw permanent(
                    "FORECAST_CONFIGURATION_INVALID",
                    "Demand forecast API URL or API key is not configured",
                    null
            );
        }
        try {
            URI uri = URI.create(normalizedBaseUrl());
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || (!("http".equalsIgnoreCase(uri.getScheme()))
                    && !("https".equalsIgnoreCase(uri.getScheme())))
                    || !properties.getPath().startsWith("/")) {
                throw new IllegalArgumentException("absolute HTTP URL required");
            }
        } catch (IllegalArgumentException exception) {
            throw permanent(
                    "FORECAST_CONFIGURATION_INVALID",
                    "Demand forecast API base URL is invalid",
                    exception
            );
        }
    }

    private String normalizedBaseUrl() {
        return properties.getBaseUrl().trim();
    }

    private static boolean containsCause(Throwable throwable, Class<?> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static PermanentStrategyGenerationException permanent(
            String code,
            String message,
            Throwable cause
    ) {
        return new PermanentStrategyGenerationException(code, STAGE, message, cause);
    }

    private static RetryableStrategyGenerationException retryable(
            String code,
            String message,
            Throwable cause
    ) {
        return new RetryableStrategyGenerationException(code, STAGE, message, cause);
    }
}
