package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;

/**
 * 실제 Gemini 계정 연결을 확인하는 로컬 전용 smoke test.
 * 별도 Gradle source set에서 수동으로만 실행하며 API 키를 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE_TEST", matches = "true")
class GeminiRecommendationLiveSmokeTest {

    @Test
    void receivesStructuredRecommendationsFromRealGeminiApi() throws IOException {
        LocalSettings local = loadLocalSettings();
        GeminiRecommendationProperties properties = new GeminiRecommendationProperties();
        properties.setApiKey(local.apiKey());
        if (local.model() != null && !local.model().isBlank()) {
            properties.setModel(local.model());
        }

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GeminiRecommendationProvider provider = new GeminiRecommendationProvider(
                new GeminiRecommendationHttpConfiguration()
                        .geminiRecommendationRestClient(properties, objectMapper),
                objectMapper,
                properties,
                new AiRecommendationPromptFactory(objectMapper)
        );

        AiRecommendationProviderResponse response;
        try {
            response = provider.recommend(request());
        } catch (RetryableStrategyGenerationException exception) {
            throw new AssertionError(
                    "Gemini live call failed with retryable error ["
                            + exception.getFailureCode()
                            + "]: "
                            + exception.getMessage(),
                    exception
            );
        }

        // store=false 호출은 공급자가 interactionId를 생략할 수 있다.
        assertThat(response.model()).contains(properties.getModel());
        assertThat(response.recommendations()).hasSize(3);
        assertThat(response.recommendations())
                .extracting(AiRecommendationProviderResponse.Recommendation::candidateId)
                .containsExactlyInAnyOrder("DISCOUNT-10", "MOVE-TO-20", "EXPAND-TO-30")
                .doesNotHaveDuplicates();
        assertThat(response.recommendations())
                .extracting(AiRecommendationProviderResponse.Recommendation::rank)
                .containsExactlyInAnyOrder(1, 2, 3);
        assertThat(response.recommendations()).allSatisfy(value -> {
            assertThat(value.optionName()).isNotBlank();
            assertThat(value.recommendationReason()).isNotBlank();
            assertThat(value.advantage()).isNotBlank();
            assertThat(value.caution()).isNotBlank();
        });
    }

    private static LocalSettings loadLocalSettings() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application-local",
                new ClassPathResource("application-local.yml")
        );
        String apiKey = environmentOrProperty(
                "AI_RECOMMENDATION_API_KEY",
                property(sources, "ai.recommendation.api-key")
        );
        String model = environmentOrProperty(
                "AI_RECOMMENDATION_MODEL",
                property(sources, "ai.recommendation.model")
        );
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException(
                    "AI_RECOMMENDATION_API_KEY or ai.recommendation.api-key must be configured"
            );
        }
        return new LocalSettings(apiKey, model);
    }

    private static String environmentOrProperty(String environmentName, String propertyValue) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null || environmentValue.isBlank()
                ? propertyValue
                : environmentValue;
    }

    private static String property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static AiRecommendationRequest request() {
        return new AiRecommendationRequest(
                "ai-strategy-recommendation-v3",
                999999L,
                3,
                3,
                new AiRecommendationRequest.BaselineInput(
                        decimal("20"), decimal("200000"), decimal("60000"),
                        decimal("0.30"), null, decimal("80"), decimal("15")
                ),
                List.of(
                        candidate(
                                "DISCOUNT-10", StrategyType.PRICE_DISCOUNT,
                                10L, 10L, decimal("45"), decimal("405000"),
                                decimal("105000"), decimal("55"), decimal("8"),
                                decimal("0"), decimal("0.10"), 1, 1
                        ),
                        candidate(
                                "MOVE-TO-20", StrategyType.RT_TRANSFER,
                                10L, 20L, decimal("40"), decimal("400000"),
                                decimal("115000"), decimal("60"), decimal("9"),
                                decimal("30000"), null, 2, 1
                        ),
                        candidate(
                                "EXPAND-TO-30", StrategyType.CHANNEL_EXPANSION,
                                10L, 30L, decimal("38"), decimal("380000"),
                                decimal("108000"), decimal("62"), decimal("10"),
                                decimal("20000"), null, 3, 2
                        )
                )
        );
    }

    private static AiRecommendationRequest.CandidateInput candidate(
            String id,
            StrategyType type,
            Long sourceSalesPointId,
            Long targetSalesPointId,
            BigDecimal salesQty,
            BigDecimal revenue,
            BigDecimal contributionMargin,
            BigDecimal remainingQty,
            BigDecimal disposalQty,
            BigDecimal actionCost,
            BigDecimal discountRate,
            int strategyPriority,
            int targetPriority
    ) {
        BigDecimal strategyPrice = discountRate == null ? null : decimal("9000");
        return new AiRecommendationRequest.CandidateInput(
                id,
                type.name() + "|" + type.name()
                        + ":W1:S" + sourceSalesPointId
                        + ">W1:S" + targetSalesPointId,
                List.of(type),
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 9, 6),
                List.of(new AiRecommendationRequest.ActionInput(
                        type, 1L, sourceSalesPointId, 1L, targetSalesPointId,
                        decimal("50"), actionCost, strategyPrice, discountRate
                )),
                new AiRecommendationRequest.SummaryInput(
                        salesQty, revenue, contributionMargin,
                        contributionMargin.divide(revenue, 4, java.math.RoundingMode.HALF_UP),
                        12, remainingQty, disposalQty, actionCost,
                        contributionMargin.subtract(actionCost)
                ),
                new AiRecommendationRequest.ComparisonInput(
                        salesQty.subtract(decimal("20")),
                        revenue.subtract(decimal("200000")),
                        contributionMargin.subtract(decimal("60000")),
                        decimal("80").subtract(remainingQty),
                        decimal("15").subtract(disposalQty),
                        contributionMargin.subtract(actionCost).subtract(decimal("60000"))
                ),
                List.of("DISCOUNT_DEMAND_UPLIFT_NOT_APPLIED"),
                new AiRecommendationRequest.PreferenceInput(
                        strategyPriority,
                        AiRecommendationRequest.PrioritySource.USER,
                        targetPriority,
                        AiRecommendationRequest.PrioritySource.USER,
                        50
                ),
                decimal("50")
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record LocalSettings(String apiKey, String model) {
    }
}
