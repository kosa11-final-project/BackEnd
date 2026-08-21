package com.stockit.backend.feature.strategy.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.stockit.backend.config.InternalApiProperties;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class MlForecastProviderTest {

    private static final String PATH = "/api/v1/demand-forecasts/daily";

    private final AtomicReference<ResponseSpec> response = new AtomicReference<>();
    private final AtomicReference<String> receivedApiKey = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    private HttpServer server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(PATH, this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsApiKeyAndParsesSuccessfulResponse() throws Exception {
        response.set(new ResponseSpec(200, successJson(), Duration.ZERO));

        StrategyForecastResponse result = provider(Duration.ofSeconds(1))
                .forecast(request());

        assertThat(result.forecastRunId()).isEqualTo("forecast-run-1");
        assertThat(receivedApiKey).hasValue("test-api-key");
        JsonNode requestJson = objectMapper.readTree(receivedBody.get());
        assertThat(requestJson.path("strategyRequestId").asLong()).isEqualTo(12345L);
        assertThat(requestJson.path("candidateSalesPointIds").get(0).asLong())
                .isEqualTo(20L);
        assertThat(requestJson.path("forecastStartDate").isTextual()).isTrue();
        assertThat(requestJson.path("forecastStartDate").asText())
                .isEqualTo("2026-08-20");
        assertThat(requestJson.path("forecastEndDate").isTextual()).isTrue();
        assertThat(requestJson.path("forecastEndDate").asText())
                .isEqualTo("2026-08-20");
    }

    @Test
    void classifiesRateLimitAsRetryable() {
        response.set(new ResponseSpec(
                429,
                "{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"retry later\"}",
                Duration.ZERO
        ));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(1)).forecast(request()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_API_RATE_LIMITED")
                );
    }

    @Test
    void classifiesForecastNotReadyAsRetryable() {
        response.set(new ResponseSpec(
                409,
                "{\"code\":\"FORECAST_NOT_READY\",\"message\":\"not ready\"}",
                Duration.ZERO
        ));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(1)).forecast(request()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_NOT_READY")
                );
    }

    @Test
    void classifiesAuthenticationFailureAsPermanentWithoutLeakingKey() {
        response.set(new ResponseSpec(401, "", Duration.ZERO));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(1)).forecast(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> {
                            assertThat(exception.getFailureCode())
                                    .isEqualTo("FORECAST_API_AUTH_FAILED");
                            assertThat(exception.getMessage())
                                    .doesNotContain("test-api-key");
                        }
                );
    }

    @Test
    void rejectsMalformedSuccessBodyAsPermanentContractFailure() {
        response.set(new ResponseSpec(200, "not-json", Duration.ZERO));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(1)).forecast(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_RESPONSE_INVALID")
                );
    }

    @Test
    void classifiesReadTimeoutAsRetryable() {
        response.set(new ResponseSpec(
                200,
                successJson(),
                Duration.ofSeconds(1)
        ));

        assertThatThrownBy(() -> provider(Duration.ofMillis(100)).forecast(request()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_API_TIMEOUT")
                );
    }

    @Test
    void rejectsBlankBaseUrlAsPermanentConfigurationFailure() {
        assertConfigurationFailure("", PATH, "test-api-key");
    }

    @Test
    void rejectsBlankApiKeyAsPermanentConfigurationFailure() {
        assertConfigurationFailure(serverBaseUrl(), PATH, "");
    }

    @Test
    void rejectsInvalidPathAsPermanentConfigurationFailure() {
        assertConfigurationFailure(serverBaseUrl(), "api/v1/forecast", "test-api-key");
    }

    @Test
    void rejectsUnsupportedUrlSchemeAsPermanentConfigurationFailure() {
        assertConfigurationFailure("ftp://localhost", PATH, "test-api-key");
    }

    @Test
    void classifiesServerErrorAsRetryable() {
        response.set(new ResponseSpec(500, "", Duration.ZERO));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(1)).forecast(request()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_API_UNAVAILABLE")
                );
    }

    @Test
    void classifiesRequestTimeoutAsRetryable() {
        response.set(new ResponseSpec(408, "", Duration.ZERO));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(1)).forecast(request()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_API_UNAVAILABLE")
                );
    }

    @Test
    void classifiesUnavailableForecastAsPermanent() {
        response.set(new ResponseSpec(
                422,
                "{\"code\":\"FORECAST_UNAVAILABLE\",\"message\":\"no data\"}",
                Duration.ZERO
        ));

        assertThatThrownBy(() -> provider(Duration.ofSeconds(1)).forecast(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("FORECAST_UNAVAILABLE")
                );
    }

    private MlForecastProvider provider(Duration readTimeout) {
        return provider(
                serverBaseUrl(),
                PATH,
                "test-api-key",
                readTimeout
        );
    }

    private MlForecastProvider provider(
            String baseUrl,
            String path,
            String apiKey,
            Duration readTimeout
    ) {
        StrategyForecastProperties properties = new StrategyForecastProperties();
        properties.setBaseUrl(baseUrl);
        properties.setPath(path);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(readTimeout);
        return new MlForecastProvider(
                new StrategyForecastHttpConfiguration()
                        .strategyForecastRestClient(properties, objectMapper),
                objectMapper,
                properties,
                new InternalApiProperties(apiKey, 0L, "ml-service")
        );
    }

    private void assertConfigurationFailure(
            String baseUrl,
            String path,
            String apiKey
    ) {
        assertThatThrownBy(() -> provider(
                baseUrl,
                path,
                apiKey,
                Duration.ofSeconds(1)
        ).forecast(request())).isInstanceOfSatisfying(
                PermanentStrategyGenerationException.class,
                exception -> assertThat(exception.getFailureCode())
                        .isEqualTo("FORECAST_CONFIGURATION_INVALID")
        );
    }

    private String serverBaseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedApiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
        receivedBody.set(new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        ));
        ResponseSpec spec = response.get();
        if (!spec.delay().isZero()) {
            try {
                Thread.sleep(spec.delay().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = spec.body().getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(spec.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static StrategyForecastRequest request() {
        return new StrategyForecastRequest(
                12345L,
                1001L,
                10L,
                List.of(20L),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20)
        );
    }

    private static String successJson() {
        return """
                {
                  "strategyRequestId": 12345,
                  "skuId": 1001,
                  "sourceSalesPointId": 10,
                  "requestedCandidateSalesPointIds": [20],
                  "forecastStartDate": "2026-08-20",
                  "forecastEndDate": "2026-08-20",
                  "forecastDays": 1,
                  "forecastRunId": "forecast-run-1",
                  "modelVersionId": 3,
                  "forecastGeneratedAt": "2026-08-20T10:15:30+09:00",
                  "salesPointForecasts": [
                    {
                      "salesPointId": 10,
                      "sourceSalesPoint": true,
                      "futureDailyPredictions": [
                        {"date": "2026-08-20", "predictedQty": 14.1}
                      ]
                    },
                    {
                      "salesPointId": 20,
                      "sourceSalesPoint": false,
                      "futureDailyPredictions": [
                        {"date": "2026-08-20", "predictedQty": 18.4}
                      ]
                    }
                  ]
                }
                """;
    }

    private record ResponseSpec(int status, String body, Duration delay) {
    }
}
