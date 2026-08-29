package com.stockit.backend.feature.strategy.observability;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.recommendation.AiRecommendationQualityEvaluation;
import com.stockit.backend.feature.strategy.recommendation.AiRecommendationProviderResponse;
import com.stockit.backend.feature.strategy.recommendation.StrategyRecommendationResult;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** AI 전략 생성 병목과 입력 규모를 저카디널리티 Metric으로 기록한다. */
@Component
public class AiStrategyGenerationMetrics {

    static final String STAGE_DURATION =
            "stockit.ai.strategy.generation.stage.duration";
    static final String INPUT_SIZE =
            "stockit.ai.strategy.generation.input.size";
    static final String CANDIDATE_COUNT =
            "stockit.ai.strategy.generation.candidate.count";
    static final String RECOMMENDATION_OUTCOME =
            "stockit.ai.strategy.recommendation.outcome";
    static final String LLM_TOKENS =
            "stockit.ai.strategy.llm.tokens";
    static final String RECOMMENDATION_QUALITY =
            "stockit.ai.strategy.recommendation.quality";
    static final String RECOMMENDATION_QUALITY_COUNT =
            "stockit.ai.strategy.recommendation.quality.count";
    static final String LLM_FAILURE =
            "stockit.ai.strategy.llm.failure";

    private final MeterRegistry meterRegistry;

    public AiStrategyGenerationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T measure(Stage stage, Supplier<T> operation) {
        long startedNanos = System.nanoTime();
        try {
            T result = operation.get();
            recordDuration(stage, "success", startedNanos);
            return result;
        } catch (RuntimeException | Error exception) {
            recordDuration(stage, "failure", startedNanos);
            throw exception;
        }
    }

    public void measure(Stage stage, Runnable operation) {
        measure(stage, () -> {
            operation.run();
            return null;
        });
    }

    public void recordInput(StrategyCalculationContext context) {
        recordInputSize("sales_point", context.salesPoints().size());
        recordInputSize("evaluation_lot", context.evaluationInventory().size());
        recordInputSize("reference_lot", context.referenceInventory().size());
        long forecastDays = ChronoUnit.DAYS.between(
                context.forecastStartDate(), context.forecastEndDate()
        ) + 1;
        recordInputSize("forecast_day", forecastDays);
    }

    public void recordCandidateCount(String phase, long count) {
        DistributionSummary.builder(CANDIDATE_COUNT)
                .description("AI strategy candidate count by processing phase")
                .baseUnit("candidates")
                .tag("phase", phase)
                .register(meterRegistry)
                .record(count);
    }

    public void recordRecommendation(StrategyRecommendationResult result) {
        String outcome;
        if (result.options().isEmpty()) {
            outcome = "no_recommendation";
        } else if ("server-rule-fallback".equals(
                result.providerMetadata().model()
        )) {
            outcome = "fallback";
        } else {
            outcome = "llm";
        }
        Counter.builder(RECOMMENDATION_OUTCOME)
                .description("AI strategy recommendation outcomes")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();

        recordCandidateCount("recommended", result.options().size());
    }

    public void recordLlmUsage(AiRecommendationProviderResponse response) {
        if (response == null) {
            return;
        }
        recordTokens("input", response.inputTokens());
        recordTokens("output", response.outputTokens());
        recordTokens("thought", response.thoughtTokens());
        recordTokens("total", response.totalTokens());
    }

    public void recordRecommendationQuality(
            AiRecommendationQualityEvaluation quality,
            String source
    ) {
        String normalizedSource = "fallback".equals(source) ? "fallback" : "llm";
        recordQuality("valid_selection_rate", quality.validSelectionRate(),
                normalizedSource);
        recordQuality("family_diversity_ratio", quality.familyDiversityRatio(),
                normalizedSource);
        recordQuality("top1_regret_rate", quality.top1RegretRate(),
                normalizedSource);
        recordBooleanQuality("top1_strategy_priority_compliance",
                quality.top1StrategyPriorityCompliant(), normalizedSource);
        recordBooleanQuality("top1_target_priority_compliance",
                quality.top1TargetPriorityCompliant(), normalizedSource);
        recordQualityCount("structural_violation",
                quality.structuralViolationCount(), normalizedSource);
        if (quality.fixedConstraintViolationCount() != null) {
            recordQualityCount("fixed_constraint_violation",
                    quality.fixedConstraintViolationCount(), normalizedSource);
        }
        recordQualityCount("distinct_strategy_family",
                quality.distinctStrategyFamilyCount(), normalizedSource);
        recordQualityCount("distinct_strategy_type",
                quality.distinctStrategyTypeCount(), normalizedSource);
        recordQualityCount("distinct_target_sales_point",
                quality.distinctTargetSalesPointCount(), normalizedSource);
    }

    public void recordLlmFailure(String failureCode) {
        Counter.builder(LLM_FAILURE)
                .description("Gemini recommendation failure categories")
                .tag("reason", normalizeFailureReason(failureCode))
                .register(meterRegistry)
                .increment();
    }

    private void recordDuration(Stage stage, String outcome, long startedNanos) {
        Timer.builder(STAGE_DURATION)
                .description("AI strategy generation stage duration")
                .tag("stage", stage.tagValue)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
    }

    private void recordInputSize(String dimension, long value) {
        DistributionSummary.builder(INPUT_SIZE)
                .description("AI strategy generation input size")
                .baseUnit("items")
                .tag("dimension", dimension)
                .register(meterRegistry)
                .record(value);
    }

    private void recordTokens(String direction, Integer tokens) {
        if (tokens == null || tokens < 0) {
            return;
        }
        DistributionSummary.builder(LLM_TOKENS)
                .description("AI strategy LLM token usage")
                .baseUnit("tokens")
                .tag("direction", direction)
                .register(meterRegistry)
                .record(tokens);
    }

    private void recordQuality(String metric, java.math.BigDecimal value, String source) {
        if (value == null || value.signum() < 0) {
            return;
        }
        DistributionSummary.builder(RECOMMENDATION_QUALITY)
                .description("AI strategy recommendation quality ratios")
                .baseUnit("ratio")
                .tag("metric", metric)
                .tag("source", source)
                .register(meterRegistry)
                .record(value.doubleValue());
    }

    private void recordBooleanQuality(String metric, Boolean value, String source) {
        if (value != null) {
            recordQuality(metric, value ? java.math.BigDecimal.ONE
                    : java.math.BigDecimal.ZERO, source);
        }
    }

    private void recordQualityCount(String metric, long value, String source) {
        DistributionSummary.builder(RECOMMENDATION_QUALITY_COUNT)
                .description("AI strategy recommendation quality counts")
                .baseUnit("items")
                .tag("metric", metric)
                .tag("source", source)
                .register(meterRegistry)
                .record(value);
    }

    private static String normalizeFailureReason(String failureCode) {
        if (failureCode == null) {
            return "other";
        }
        return switch (failureCode) {
            case "LLM_API_TIMEOUT" -> "timeout";
            case "LLM_API_RATE_LIMITED" -> "rate_limited";
            case "LLM_API_QUOTA_EXHAUSTED" -> "quota_exhausted";
            case "LLM_API_UNAVAILABLE", "LLM_INTERACTION_FAILED",
                    "LLM_INTERACTION_PENDING" -> "unavailable";
            case "LLM_INTERACTION_INCOMPLETE" -> "incomplete";
            case "LLM_INTERACTION_BUDGET_EXCEEDED" -> "budget_exceeded";
            case "LLM_RESPONSE_INVALID" -> "invalid_response";
            case "LLM_API_AUTH_FAILED" -> "authentication";
            case "LLM_API_REQUEST_REJECTED" -> "request_rejected";
            default -> "other";
        };
    }

    public enum Stage {
        TOTAL_GENERATION("total_generation"),
        FORECAST_API("forecast_api"),
        CONTEXT_LOAD("context_load"),
        BASELINE_SIMULATION("baseline_simulation"),
        CANDIDATE_GENERATION("candidate_generation"),
        CANDIDATE_SIMULATION("candidate_simulation"),
        CANDIDATE_PRESELECTION("candidate_preselection"),
        LLM_RECOMMENDATION("llm_recommendation"),
        FINALIZATION("finalization");

        private final String tagValue;

        Stage(String tagValue) {
            this.tagValue = tagValue;
        }
    }
}
