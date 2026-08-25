package com.stockit.backend.feature.strategy.recommendation;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final int MAX_ERROR_DETAIL_LENGTH = 300;
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
        try {
            return restClient.post().uri(interactionsUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(API_KEY_HEADER, properties.getApiKey())
                    .body(requestBody(request))
                    .exchange((httpRequest, response) -> parseResponse(
                            response.getStatusCode(), response.getBody().readAllBytes()
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
        for (String field : List.of("optionName", "recommendationReason",
                "advantage", "caution")) {
            itemProperties.put(field, Map.of("type", "string"));
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
            HttpStatusCode status, byte[] body
    ) {
        if (status.is2xxSuccessful()) {
            try {
                GeminiInteraction interaction = objectMapper.readValue(
                        body, GeminiInteraction.class
                );
                if (!"completed".equals(interaction.status())) {
                    throw permanent("LLM_INTERACTION_INCOMPLETE",
                            "Gemini interaction did not complete", null);
                }
                String json = interaction.steps() == null ? null
                        : interaction.steps().stream()
                        .filter(step -> "model_output".equals(step.type()))
                        .flatMap(step -> step.content() == null
                                ? java.util.stream.Stream.empty()
                                : step.content().stream())
                        .filter(content -> "text".equals(content.type()))
                        .map(GeminiContent::text).filter(text -> text != null && !text.isBlank())
                        .reduce((first, second) -> first + second).orElse(null);
                if (json == null) {
                    throw permanent("LLM_RESPONSE_INVALID",
                            "Gemini response contains no structured text", null);
                }
                RecommendationPayload payload = objectMapper.readValue(
                        json, RecommendationPayload.class
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
                throw permanent("LLM_RESPONSE_INVALID",
                        "Gemini structured response is invalid", exception);
            }
        }
        if (status.value() == 408) {
            throw retryable(
                    "LLM_API_TIMEOUT",
                    failureMessage("Gemini recommendation API timed out (HTTP 408)", body),
                    null
            );
        }
        if (status.value() == 429) {
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
            String id, String model, String status, List<GeminiStep> steps, GeminiUsage usage
    ) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiStep(String type, List<GeminiContent> content) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiContent(String type, String text) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiUsage(
            @com.fasterxml.jackson.annotation.JsonProperty("total_input_tokens") Integer totalInputTokens,
            @com.fasterxml.jackson.annotation.JsonProperty("total_output_tokens") Integer totalOutputTokens
    ) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RecommendationPayload(
            List<AiRecommendationProviderResponse.Recommendation> recommendations
    ) {}
}
