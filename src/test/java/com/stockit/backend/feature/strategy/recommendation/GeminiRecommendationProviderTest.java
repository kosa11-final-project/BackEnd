package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(OutputCaptureExtension.class)
class GeminiRecommendationProviderTest {

    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1beta/interactions", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void defaultsToGemini35Flash() {
        GeminiRecommendationProperties properties =
                new GeminiRecommendationProperties();

        assertThat(properties.getModel()).isEqualTo("gemini-3.5-flash");
        assertThat(properties.getMaxOutputTokens()).isEqualTo(4096);
    }

    @Test
    void sendsStructuredSchemaAndParsesInteractionOutput() throws Exception {
        responseBody.set(successJson());

        AiRecommendationProviderResponse result = provider("test-key").recommend(request());

        assertThat(result.interactionId()).isEqualTo("interaction-1");
        assertThat(result.recommendations()).singleElement().satisfies(value -> {
            assertThat(value.candidateId()).isEqualTo("CAND-1");
            assertThat(value.rank()).isEqualTo(1);
        });
        assertThat(apiKey).hasValue("test-key");
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("gemini-3.5-flash");
        assertThat(body.path("generation_config").path("max_output_tokens").asInt())
                .isEqualTo(4096);
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.path("input").asText())
                .contains("\"schemaVersion\":\"ai-strategy-recommendation-v4\"")
                .contains("\"strategyFamilyId\":\"PRICE_DISCOUNT|")
                .contains("\"strategyPrioritySource\":\"USER\"")
                .contains("\"targetPrioritySource\":\"USER\"")
                .contains("\"expectedDisposalCost\":2")
                .contains("\"avoidedHoldingCost\":2");
        JsonNode enumValues = body.path("response_format").path("schema")
                .path("properties").path("recommendations").path("items")
                .path("properties").path("candidateId").path("enum");
        assertThat(enumValues.get(0).asText()).isEqualTo("CAND-1");
        JsonNode responseProperties = body.path("response_format").path("schema")
                .path("properties").path("recommendations").path("items")
                .path("properties");
        assertThat(responseProperties.path("optionName").path("maxLength").asInt())
                .isEqualTo(100);
        assertThat(responseProperties.path("recommendationReason")
                .path("maxLength").asInt()).isEqualTo(300);
        assertThat(responseProperties.path("advantage").path("maxLength").asInt())
                .isEqualTo(300);
        assertThat(responseProperties.path("caution").path("maxLength").asInt())
                .isEqualTo(300);
        assertThat(body.path("input").asText())
                .contains("한국어 1~2문장")
                .contains("동일한 수치와 설명을 여러 필드에서 반복하지 마세요");
    }

    @Test
    void classifiesRateLimitAsRetryable() {
        status.set(429);
        responseBody.set("{}");
        assertThatThrownBy(() -> provider("test-key").recommend(request()))
                .isInstanceOf(RetryableStrategyGenerationException.class);
    }

    @Test
    void classifiesAuthenticationFailureAsPermanentAndPreservesSafeDetail() {
        status.set(403);
        responseBody.set("""
                {"error":{"code":403,"status":"PERMISSION_DENIED",
                "message":"API key is not authorized"}}
                """);

        assertThatThrownBy(() -> provider("test-key").recommend(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> {
                            assertThat(exception.getFailureCode())
                                    .isEqualTo("LLM_API_AUTH_FAILED");
                            assertThat(exception.getMessage())
                                    .contains("PERMISSION_DENIED")
                                    .doesNotContain("test-key");
                        }
                );
    }

    @Test
    void classifiesServerFailureAsRetryable() {
        status.set(503);
        responseBody.set("{\"error\":{\"status\":\"UNAVAILABLE\"}}");

        assertThatThrownBy(() -> provider("test-key").recommend(request()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("LLM_API_UNAVAILABLE")
                );
    }

    @Test
    void logsSafeDiagnosticsForIncompleteInteraction(CapturedOutput output) {
        responseBody.set("""
                {"id":"interaction-1","model":"gemini-3.5-flash",
                "status":"incomplete",
                "steps":[{"type":"model_output","content":[
                  {"type":"text","text":"partial output"}
                ]}],
                "usage":{"total_input_tokens":1100,"total_output_tokens":2048,
                  "total_thought_tokens":320,"total_tokens":3468},
                "errors":[{"code":"MAX_TOKENS",
                  "message":"output exceeded test-key token limit"}]}
                """);

        assertThatThrownBy(() -> provider("test-key").recommend(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("LLM_INTERACTION_INCOMPLETE")
                );
        assertThat(output)
                .contains("strategyCaseId=1")
                .contains("responseModel=gemini-3.5-flash")
                .contains("status=incomplete")
                .contains("outputTokens=2048")
                .contains("thoughtTokens=320")
                .contains("totalTokens=3468")
                .contains("modelOutputPresent=true")
                .contains("modelOutputLength=14")
                .contains("errorCodes=[MAX_TOKENS]")
                .contains("[REDACTED]")
                .doesNotContain("output exceeded test-key");
    }

    @Test
    void classifiesBudgetExceededForDeterministicFallback() {
        responseBody.set("""
                {"id":"interaction-1","model":"gemini-3.5-flash",
                "status":"budget_exceeded","steps":[],
                "usage":{"total_input_tokens":1100,"total_output_tokens":0}}
                """);

        assertThatThrownBy(() -> provider("test-key").recommend(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("LLM_INTERACTION_BUDGET_EXCEEDED")
                );
    }

    @Test
    void classifiesPendingInteractionAsRetryable() {
        responseBody.set("""
                {"id":"interaction-1","model":"gemini-3.5-flash",
                "status":"in_progress","steps":[]}
                """);

        assertThatThrownBy(() -> provider("test-key").recommend(request()))
                .isInstanceOfSatisfying(
                        RetryableStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("LLM_INTERACTION_PENDING")
                );
    }

    @Test
    void classifiesRequiresActionAsPermanentConfigurationMismatch() {
        responseBody.set("""
                {"id":"interaction-1","model":"gemini-3.5-flash",
                "status":"requires_action","steps":[]}
                """);

        assertThatThrownBy(() -> provider("test-key").recommend(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("LLM_INTERACTION_REQUIRES_ACTION")
                );
    }

    @Test
    void rejectsSuccessfulInteractionWithoutModelOutputAsPermanent() {
        responseBody.set("""
                {"id":"interaction-1","model":"gemini-3.5-flash",
                "status":"completed","steps":[]}
                """);

        assertThatThrownBy(() -> provider("test-key").recommend(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("LLM_RESPONSE_INVALID")
                );
    }

    @Test
    void rejectsBlankApiKeyWithoutSendingRequest() {
        assertThatThrownBy(() -> provider("").recommend(request()))
                .isInstanceOfSatisfying(
                        PermanentStrategyGenerationException.class,
                        exception -> assertThat(exception.getFailureCode())
                                .isEqualTo("LLM_CONFIGURATION_INVALID")
                );
    }

    private GeminiRecommendationProvider provider(String key) {
        GeminiRecommendationProperties properties = new GeminiRecommendationProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setApiKey(key);
        properties.setModel("gemini-3.5-flash");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        return new GeminiRecommendationProvider(
                new GeminiRecommendationHttpConfiguration()
                        .geminiRecommendationRestClient(properties, objectMapper),
                objectMapper, properties, new AiRecommendationPromptFactory(objectMapper)
        );
    }

    private static AiRecommendationRequest request() {
        var action = new AiRecommendationRequest.ActionInput(
                StrategyType.PRICE_DISCOUNT, 1L, 10L, 1L, 10L,
                BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("900"),
                new BigDecimal("0.10")
        );
        var candidate = new AiRecommendationRequest.CandidateInput(
                "CAND-1", "PRICE_DISCOUNT|PRICE_DISCOUNT:W1:S10>W1:S10",
                List.of(StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 31),
                List.of(action),
                new AiRecommendationRequest.SummaryInput(
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.ONE, 1, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("2"), new BigDecimal("3"),
                        BigDecimal.ONE, BigDecimal.TEN
                ),
                new AiRecommendationRequest.ComparisonInput(
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, new BigDecimal("2"), BigDecimal.ONE
                ), List.of(), new AiRecommendationRequest.PreferenceInput(
                        1, AiRecommendationRequest.PrioritySource.USER,
                        1, AiRecommendationRequest.PrioritySource.USER, 100
                ),
                BigDecimal.TEN
        );
        return new AiRecommendationRequest(
                "ai-strategy-recommendation-v4", 1L, 1, 1,
                new AiRecommendationRequest.BaselineInput(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, null, BigDecimal.TEN, BigDecimal.ZERO,
                        new BigDecimal("2"), new BigDecimal("3")
                ), List.of(candidate)
        );
    }

    private void handle(HttpExchange exchange) throws IOException {
        apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
        byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status.get(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String successJson() {
        return """
                {
                  "id":"interaction-1",
                  "model":"gemini-3.5-flash",
                  "status":"completed",
                  "steps":[{
                    "type":"model_output",
                    "content":[{
                      "type":"text",
                      "text":"{\\"recommendations\\":[{\\"candidateId\\":\\"CAND-1\\",\\"rank\\":1,\\"optionName\\":\\"할인 전략\\",\\"recommendationReason\\":\\"판매 개선\\",\\"advantage\\":\\"재고 감소\\",\\"caution\\":\\"공헌이익 확인\\"}]}"
                    }]
                  }],
                  "usage":{"total_input_tokens":100,"total_output_tokens":50}
                }
                """;
    }
}
