package com.stockit.backend.feature.strategy.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.observability.AiStrategyGenerationMetrics.Stage;
import com.stockit.backend.feature.strategy.recommendation.AiRecommendationQualityEvaluation;
import com.stockit.backend.feature.strategy.recommendation.AiRecommendationProviderResponse;
import com.stockit.backend.feature.strategy.recommendation.StrategyRecommendationResult;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AiStrategyGenerationMetricsTest {

    @Test
    void recordsStageSuccessAndFailureWithoutHighCardinalityIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiStrategyGenerationMetrics metrics = new AiStrategyGenerationMetrics(registry);

        assertThat(metrics.measure(Stage.CANDIDATE_SIMULATION, () -> "ok"))
                .isEqualTo("ok");
        assertThatThrownBy(() -> metrics.measure(
                Stage.CANDIDATE_SIMULATION,
                () -> { throw new IllegalStateException("failed"); }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(registry.find(AiStrategyGenerationMetrics.STAGE_DURATION)
                .tags("stage", "candidate_simulation", "outcome", "success")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.find(AiStrategyGenerationMetrics.STAGE_DURATION)
                .tags("stage", "candidate_simulation", "outcome", "failure")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("strategyCaseId")
                                || tag.getKey().equals("skuId"))
        );
    }

    @Test
    void recordsInputCandidateRecommendationAndTokenDistributions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiStrategyGenerationMetrics metrics = new AiStrategyGenerationMetrics(registry);

        metrics.recordInput(context());
        metrics.recordCandidateCount("generated", 120);
        metrics.recordRecommendation(recommendation());
        metrics.recordLlmUsage(new AiRecommendationProviderResponse(
                "interaction-1", "gemini", 900, 120, 300, 1320,
                List.of()
        ));

        assertThat(registry.find(AiStrategyGenerationMetrics.INPUT_SIZE)
                .tag("dimension", "forecast_day")
                .summary().totalAmount()).isEqualTo(8.0);
        assertThat(registry.find(AiStrategyGenerationMetrics.CANDIDATE_COUNT)
                .tag("phase", "generated")
                .summary().totalAmount()).isEqualTo(120.0);
        assertThat(registry.find(AiStrategyGenerationMetrics.RECOMMENDATION_OUTCOME)
                .tag("outcome", "llm")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find(AiStrategyGenerationMetrics.LLM_TOKENS)
                .tag("direction", "input")
                .summary().totalAmount()).isEqualTo(900.0);
        assertThat(registry.find(AiStrategyGenerationMetrics.LLM_TOKENS)
                .tag("direction", "thought")
                .summary().totalAmount()).isEqualTo(300.0);
        assertThat(registry.find(AiStrategyGenerationMetrics.LLM_TOKENS)
                .tag("direction", "total")
                .summary().totalAmount()).isEqualTo(1320.0);
    }

    @Test
    void recordsRecommendationQualityAndNormalizedFailureReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiStrategyGenerationMetrics metrics = new AiStrategyGenerationMetrics(registry);
        AiRecommendationQualityEvaluation quality =
                new AiRecommendationQualityEvaluation(
                        20, 4, 4, 0, 4, 3, 2,
                        new BigDecimal("100000"), new BigDecimal("90000"),
                        new BigDecimal("10000"), new BigDecimal("0.100000"),
                        true, false, 0
                );

        metrics.recordRecommendationQuality(quality, "llm");
        metrics.recordLlmFailure("LLM_API_RATE_LIMITED");

        assertThat(registry.find(AiStrategyGenerationMetrics.RECOMMENDATION_QUALITY)
                .tags("metric", "top1_regret_rate", "source", "llm")
                .summary().totalAmount()).isEqualTo(0.1);
        assertThat(registry.find(AiStrategyGenerationMetrics.RECOMMENDATION_QUALITY)
                .tags("metric", "top1_strategy_priority_compliance", "source", "llm")
                .summary().totalAmount()).isEqualTo(1.0);
        assertThat(registry.find(AiStrategyGenerationMetrics.RECOMMENDATION_QUALITY)
                .tags("metric", "top1_target_priority_compliance", "source", "llm")
                .summary().totalAmount()).isZero();
        assertThat(registry.find(
                        AiStrategyGenerationMetrics.RECOMMENDATION_QUALITY_COUNT)
                .tags("metric", "distinct_strategy_family", "source", "llm")
                .summary().totalAmount()).isEqualTo(4.0);
        assertThat(registry.find(AiStrategyGenerationMetrics.LLM_FAILURE)
                .tag("reason", "rate_limited")
                .counter().count()).isEqualTo(1.0);
    }

    private static StrategyCalculationContext context() {
        StrategyCalculationContext.InventoryLot lot =
                new StrategyCalculationContext.InventoryLot(
                        1L, 1L, 501L, 10L, 10L,
                        BigDecimal.TEN, BigDecimal.ZERO,
                        null, LocalDate.of(2026, 8, 1), null, null, "AVAILABLE"
                );
        return new StrategyCalculationContext(
                1L,
                10L,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 27),
                new StrategyCalculationContext.Sku(
                        1L, "SKU-1", "상품", "EA", BigDecimal.ONE
                ),
                BigDecimal.ONE,
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), null, null
                ),
                List.of(lot),
                List.of(lot),
                List.of(),
                Map.of(),
                new StrategyCalculationContext.ForecastMetadata(
                        "run-1",
                        1L,
                        OffsetDateTime.of(
                                2026, 8, 20, 9, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private static StrategyRecommendationResult recommendation() {
        return new StrategyRecommendationResult(
                1L,
                context(),
                org.mockito.Mockito.mock(BaselineSimulation.class),
                List.of(new StrategyRecommendationResult.RecommendedOption(
                        1, "대안", "이유", "장점", "주의",
                        org.mockito.Mockito.mock(
                                com.stockit.backend.feature.strategy.calculation.domain
                                        .StrategyCandidateEvaluationResult
                                        .EvaluatedCandidate.class
                        )
                )),
                null,
                new StrategyRecommendationResult.ProviderMetadata(
                        "interaction-1", "gemini", 900, 120
                )
        );
    }
}
