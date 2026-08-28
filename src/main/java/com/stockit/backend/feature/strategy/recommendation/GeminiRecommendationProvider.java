package com.stockit.backend.feature.strategy.recommendation;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;

/** Gemini Interactions API Structured Output 어댑터. */
@Component
public class GeminiRecommendationProvider implements AiRecommendationProvider {

    private static final Logger log = LoggerFactory.getLogger(
            GeminiRecommendationProvider.class
    );
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final int MAX_ERROR_DETAIL_LENGTH = 300;
    private static final int MAX_OPTION_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final StrategyGenerationStage STAGE =
            StrategyGenerationStage.STRATEGY_GENERATING;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final GeminiRecommendationProperties properties;
    private final AiRecommendationPromptFactory promptFactory;

    public GeminiRecommendationProvider(
            @Qualifier("geminiRecommendationRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            GeminiRecommendationProperties properties,
            AiRecommendationPromptFactory promptFactory
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.promptFactory = promptFactory;
    }

    @Override
    public AiRecommendationProviderResponse recommend(AiRecommendationRequest request) {
        validateConfiguration();
        Map<String, Object> body = requestBody(request);
        int promptLength = body.get("input") instanceof String input
                ? input.length() : 0;
        long startedNanos = System.nanoTime();
        try {
            return restClient.post().uri(interactionsUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(API_KEY_HEADER, properties.getApiKey())
                    .body(body)
                    .exchange((httpRequest, response) -> parseResponse(
                            response.getStatusCode(), response.getBody().readAllBytes(),
                            request, promptLength, startedNanos
                    ));
        } catch (PermanentStrategyGenerationException
                 | RetryableStrategyGenerationException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            String code = containsCause(exception, SocketTimeoutException.class)
                    || containsCause(exception, HttpTimeoutException.class)
                    ? "LLM_API_TIMEOUT" : "LLM_API_UNAVAILABLE";
            throw retryable(code, "Gemini recommendation API connection failed", exception);
        } catch (RuntimeException exception) {
            throw permanent("LLM_CLIENT_ERROR",
                    "Gemini recommendation client failed unexpectedly", exception);
        }
    }

    private Map<String, Object> requestBody(AiRecommendationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", promptFactory.create(request));
        body.put("response_format", responseFormat(request));
        body.put("stream", false);
        body.put("store", false);
        body.put("background", false);
        body.put("generation_config", Map.of(
                "max_output_tokens", properties.getMaxOutputTokens(),
                "seed", properties.getSeed(),
                "thinking_level", "low",
                "thinking_summaries", "none"
        ));
        return body;
    }

    private Map<String, Object> responseFormat(AiRecommendationRequest request) {
        List<String> ids = request.candidates().stream()
                .map(AiRecommendationRequest.CandidateInput::candidateId).toList();
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("candidateId", Map.of("type", "string", "enum", ids));
        itemProperties.put("rank", Map.of("type", "integer", "minimum", 1,
                "maximum", request.maximumRecommendationCount()));
        itemProperties.put("optionName", Map.of(
                "type", "string", "maxLength", MAX_OPTION_NAME_LENGTH
        ));
        for (String field : List.of("recommendationReason", "advantage", "caution")) {
            itemProperties.put(field, Map.of(
                    "type", "string", "maxLength", MAX_DESCRIPTION_LENGTH
            ));
        }
        Map<String, Object> item = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", itemProperties,
                "required", List.of("candidateId", "rank", "optionName",
                        "recommendationReason", "advantage", "caution")
        );
        Map<String, Object> schema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of("recommendations", Map.of(
                        "type", "array",
                        "minItems", request.minimumRecommendationCount(),
                        "maxItems", request.maximumRecommendationCount(),
                        "items", item
                )),
                "required", List.of("recommendations")
        );
        return Map.of("type", "text", "mime_type", "application/json", "schema", schema);
    }

    private AiRecommendationProviderResponse parseResponse(
            HttpStatusCode status,
            byte[] body,
            AiRecommendationRequest request,
            int promptLength,
            long startedNanos
    ) {
        if (status.is2xxSuccessful()) {
            try {
                GeminiInteraction interaction = objectMapper.readValue(
                        body, GeminiInteraction.class
                );
                String modelOutput = modelOutput(interaction);
                logInteractionDiagnostics(
                        status, interaction, request, promptLength,
                        modelOutput, startedNanos
                );
                if (!"completed".equals(interaction.status())) {
                    throw classifyInteractionStatus(interaction.status());
                }
                if (modelOutput == null) {
                    throw permanent("LLM_RESPONSE_INVALID",
                            "Gemini response contains no structured text", null);
                }
                RecommendationPayload payload = objectMapper.readValue(
                        modelOutput, RecommendationPayload.class
                );
                GeminiUsage usage = interaction.usage();
                return new AiRecommendationProviderResponse(
                        interaction.id(), interaction.model(),
                        usage == null ? null : usage.totalInputTokens(),
                        usage == null ? null : usage.totalOutputTokens(),
                        payload.recommendations()
                );
            } catch (PermanentStrategyGenerationException exception) {
                throw exception;
            } catch (IOException | InvalidAiRecommendationException exception) {
                log.warn(
                        "Gemini interaction response could not be parsed. "
                                + "strategyCaseId={}, httpStatus={}, responseBytes={}, "
                                + "elapsedMs={}",
                        request.strategyCaseId(), status.value(), body.length,
                        elapsedMillis(startedNanos)
                );
                throw permanent("LLM_RESPONSE_INVALID",
                        "Gemini structured response is invalid", exception);
            }
        }
        log.warn(
                "Gemini recommendation HTTP request failed. strategyCaseId={}, "
                        + "httpStatus={}, responseBytes={}, elapsedMs={}",
                request.strategyCaseId(), status.value(), body.length,
                elapsedMillis(startedNanos)
        );
        if (status.value() == 408) {
            throw retryable(
                    "LLM_API_TIMEOUT",
                    failureMessage("Gemini recommendation API timed out (HTTP 408)", body),
                    null
            );
        }
        if (status.value() == 429) {
            if (isQuotaExhausted(body)) {
                throw permanent(
                        "LLM_API_QUOTA_EXHAUSTED",
                        "Gemini recommendation API quota is exhausted",
                        null
                );
            }
            throw retryable(
                    "LLM_API_RATE_LIMITED",
                    failureMessage(
                            "Gemini recommendation API rate limit exceeded (HTTP 429)",
                            body
                    ),
                    null
            );
        }
        if (status.is5xxServerError()) {
            throw retryable(
                    "LLM_API_UNAVAILABLE",
                    failureMessage(
                            "Gemini recommendation API failed (HTTP "
                                    + status.value() + ")",
                            body
                    ),
                    null
            );
        }
        if (status.value() == 401 || status.value() == 403) {
            throw permanent("LLM_API_AUTH_FAILED",
                    failureMessage(
                            "Gemini recommendation API authentication failed",
                            body
                    ), null);
        }
        throw permanent("LLM_API_REQUEST_REJECTED",
                failureMessage(
                        "Gemini recommendation API rejected the request",
                        body
                ), null);
    }

    private RuntimeException classifyInteractionStatus(String status) {
        if (status == null || status.isBlank()) {
            return permanent(
                    "LLM_RESPONSE_INVALID",
                    "Gemini interaction status is missing",
                    null
            );
        }
        return switch (status) {
            // 같은 입력·seed·출력 제한으로 다시 호출해도 반복될 가능성이 높아
            // 상위 추천 서비스가 검증된 서버 후보로 즉시 fallback한다.
            case "incomplete" -> permanent(
                    "LLM_INTERACTION_INCOMPLETE",
                    "Gemini interaction result is incomplete",
                    null
            );
            case "budget_exceeded" -> permanent(
                    "LLM_INTERACTION_BUDGET_EXCEEDED",
                    "Gemini interaction token budget was exceeded",
                    null
            );
            // background=false 요청에서 도착한 비종결·공급자 실패 상태는
            // 기존 RabbitMQ 지연 재시도로 새 interaction을 생성한다.
            case "queued", "in_progress" -> retryable(
                    "LLM_INTERACTION_PENDING",
                    "Gemini interaction is not complete yet",
                    null
            );
            case "failed", "cancelled" -> retryable(
                    "LLM_INTERACTION_FAILED",
                    "Gemini interaction failed before completion",
                    null
            );
            case "requires_action" -> permanent(
                    "LLM_INTERACTION_REQUIRES_ACTION",
                    "Gemini interaction unexpectedly requires an action",
                    null
            );
            default -> permanent(
                    "LLM_RESPONSE_INVALID",
                    "Gemini interaction returned an unknown status",
                    null
            );
        };
    }

    private String modelOutput(GeminiInteraction interaction) {
        return interaction.steps() == null ? null
                : interaction.steps().stream()
                .filter(step -> "model_output".equals(step.type()))
                .flatMap(step -> step.content() == null
                        ? java.util.stream.Stream.empty()
                        : step.content().stream())
                .filter(content -> "text".equals(content.type()))
                .map(GeminiContent::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((first, second) -> first + second)
                .orElse(null);
    }

    /** 프롬프트와 원문 응답 없이 Interaction 상태와 사용량만 진단 로그로 남긴다. */
    private void logInteractionDiagnostics(
            HttpStatusCode httpStatus,
            GeminiInteraction interaction,
            AiRecommendationRequest request,
            int promptLength,
            String modelOutput,
            long startedNanos
    ) {
        GeminiUsage usage = interaction.usage();
        List<GeminiStep> steps = interaction.steps() == null
                ? Collections.emptyList() : interaction.steps();
        List<GeminiError> errors = interaction.errors() == null
                ? Collections.emptyList() : interaction.errors();
        List<String> stepTypes = steps.stream()
                .map(GeminiStep::type)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .toList();
        List<String> errorCodes = errors.stream()
                .map(GeminiError::code)
                .filter(code -> code != null && !code.isBlank())
                .map(this::sanitizeDetail)
                .toList();
        List<String> errorMessages = errors.stream()
                .map(GeminiError::message)
                .filter(message -> message != null && !message.isBlank())
                .map(this::sanitizeDetail)
                .toList();
        String message = "Gemini interaction diagnostics. strategyCaseId={}, "
                + "httpStatus={}, interactionId={}, configuredModel={}, responseModel={}, "
                + "status={}, candidateCount={}, promptLength={}, maxOutputTokens={}, "
                + "elapsedMs={}, inputTokens={}, outputTokens={}, thoughtTokens={}, "
                + "totalTokens={}, stepTypes={}, modelOutputPresent={}, "
                + "modelOutputLength={}, errorCodes={}, errorMessages={}";
        Object[] arguments = {
                request.strategyCaseId(), httpStatus.value(), interaction.id(),
                properties.getModel(), interaction.model(), interaction.status(),
                request.candidates().size(), promptLength,
                properties.getMaxOutputTokens(), elapsedMillis(startedNanos),
                usage == null ? null : usage.totalInputTokens(),
                usage == null ? null : usage.totalOutputTokens(),
                usage == null ? null : usage.totalThoughtTokens(),
                usage == null ? null : usage.totalTokens(),
                stepTypes, modelOutput != null,
                modelOutput == null ? 0 : modelOutput.length(),
                errorCodes, errorMessages
        };
        if ("completed".equals(interaction.status())) {
            log.info(message, arguments);
        } else {
            log.warn(message, arguments);
        }
    }

    private String sanitizeDetail(String value) {
        String detail = value.replaceAll("\\s+", " ").trim();
        String apiKey = properties.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            detail = detail.replace(apiKey, "[REDACTED]");
        }
        if (detail.length() > MAX_ERROR_DETAIL_LENGTH) {
            return detail.substring(0, MAX_ERROR_DETAIL_LENGTH) + "...";
        }
        return detail;
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    /** 민감한 요청 본문 없이 공급자 오류 코드와 메시지만 제한된 길이로 보존한다 */
    private String failureMessage(String baseMessage, byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null) {
                return baseMessage;
            }
            JsonNode error = root.path("error");
            if (error.isMissingNode() || error.isNull()) {
                return baseMessage;
            }
            String code = firstText(error, "status", "code");
            String message = firstText(error, "message");
            String detail = ((code == null ? "" : code + ": ")
                    + (message == null ? "" : message))
                    .replaceAll("\\s+", " ")
                    .trim();
            if (detail.isEmpty()) {
                return baseMessage;
            }
            String apiKey = properties.getApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                detail = detail.replace(apiKey, "[REDACTED]");
            }
            if (detail.length() > MAX_ERROR_DETAIL_LENGTH) {
                detail = detail.substring(0, MAX_ERROR_DETAIL_LENGTH) + "...";
            }
            return baseMessage + " - " + detail;
        } catch (IOException exception) {
            return baseMessage;
        }
    }

    /** 분 단위 제한과 달리 짧은 재시도로 회복되지 않는 할당량·결제 한도를 구분한다. */
    private boolean isQuotaExhausted(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null) return false;
            String message = firstText(root.path("error"), "message");
            if (message == null) return false;
            String normalized = message.toLowerCase(java.util.Locale.ROOT);
            return normalized.contains("quota")
                    && (normalized.contains("exceed")
                    || normalized.contains("billing")
                    || normalized.contains("plan"));
        } catch (IOException exception) {
            return false;
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isValueNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private void validateConfiguration() {
        try {
            URI base = URI.create(properties.getBaseUrl().trim());
            if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                    || properties.getModel() == null || properties.getModel().isBlank()
                    || !base.isAbsolute() || base.getHost() == null
                    || !List.of("http", "https").contains(base.getScheme().toLowerCase())
                    || properties.getPath() == null
                    || !properties.getPath().startsWith("/")) {
                throw new IllegalArgumentException("invalid Gemini configuration");
            }
        } catch (RuntimeException exception) {
            throw permanent("LLM_CONFIGURATION_INVALID",
                    "Gemini URL, model, or API key is not configured", exception);
        }
    }

    private URI interactionsUri() {
        String base = properties.getBaseUrl().trim();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + properties.getPath());
    }

    private static boolean containsCause(Throwable value, Class<?> type) {
        for (Throwable current = value; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private static PermanentStrategyGenerationException permanent(
            String code, String message, Throwable cause
    ) {
        return new PermanentStrategyGenerationException(code, STAGE, message, cause);
    }

    private static RetryableStrategyGenerationException retryable(
            String code, String message, Throwable cause
    ) {
        return new RetryableStrategyGenerationException(code, STAGE, message, cause);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiInteraction(
            String id,
            String model,
            String status,
            List<GeminiStep> steps,
            GeminiUsage usage,
            List<GeminiError> errors
    ) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiStep(String type, List<GeminiContent> content) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiContent(String type, String text) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiError(String code, String message) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiUsage(
            @com.fasterxml.jackson.annotation.JsonProperty("total_input_tokens") Integer totalInputTokens,
            @com.fasterxml.jackson.annotation.JsonProperty("total_output_tokens") Integer totalOutputTokens,
            @com.fasterxml.jackson.annotation.JsonProperty("total_thought_tokens") Integer totalThoughtTokens,
            @com.fasterxml.jackson.annotation.JsonProperty("total_tokens") Integer totalTokens
    ) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RecommendationPayload(
            List<AiRecommendationProviderResponse.Recommendation> recommendations
    ) {}
}
