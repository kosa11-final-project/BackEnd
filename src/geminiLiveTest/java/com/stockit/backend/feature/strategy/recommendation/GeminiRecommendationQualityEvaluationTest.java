package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;

/**
 * 고정 후보 시나리오를 실제 Gemini에 반복 전달해 품질·지연·토큰·안정성을 같은
 * 계산식으로 비교하는 수동 평가 Harness다. 후보 수 자체를 바꾸는 실험은 하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE_TEST", matches = "true")
class GeminiRecommendationQualityEvaluationTest {

    private static final int DEFAULT_REPETITIONS = 3;
    @Test
    void evaluatesFixedRecommendationScenariosAndWritesReport() throws IOException {
        LocalSettings local = loadLocalSettings();
        GeminiRecommendationProperties properties = new GeminiRecommendationProperties();
        properties.setApiKey(local.apiKey());
        if (local.model() != null && !local.model().isBlank()) {
            properties.setModel(local.model());
        }
        properties.setThinkingLevel(evaluationThinkingLevel());

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GeminiRecommendationProvider provider = new GeminiRecommendationProvider(
                new GeminiRecommendationHttpConfiguration()
                        .geminiRecommendationRestClient(properties, objectMapper),
                objectMapper,
                properties,
                new AiRecommendationPromptFactory(objectMapper)
        );
        AiRecommendationQualityEvaluator evaluator =
                new AiRecommendationQualityEvaluator();
        int repetitions = repetitions();
        List<Observation> observations = new ArrayList<>();

        evaluation:
        for (Scenario scenario : selectedScenarios()) {
            for (int run = 1; run <= repetitions; run++) {
                long startedNanos = System.nanoTime();
                try {
                    AiRecommendationProviderResponse response = provider.recommend(
                            scenario.request()
                    );
                    long latencyMillis = elapsedMillis(startedNanos);
                    AiRecommendationQualityEvaluation quality = evaluator.evaluate(
                            scenario.request(), response
                    );
                    assertThat(quality.structuralViolationCount())
                            .as("scenario=%s, run=%s", scenario.name(), run)
                            .isZero();
                    assertThat(response.recommendations())
                            .hasSizeBetween(
                                    scenario.request().minimumRecommendationCount(),
                                    scenario.request().maximumRecommendationCount()
                            );
                    observations.add(Observation.success(
                            scenario.name(), run, latencyMillis,
                            response.inputTokens(), response.outputTokens(),
                            response.thoughtTokens(), response.totalTokens(),
                            rankedCandidateIds(response), quality
                    ));
                } catch (PermanentStrategyGenerationException exception) {
                    observations.add(Observation.failure(
                            scenario.name(), run, elapsedMillis(startedNanos),
                            exception.getFailureCode()
                    ));
                    if ("LLM_API_QUOTA_EXHAUSTED".equals(
                            exception.getFailureCode())) {
                        break evaluation;
                    }
                } catch (RetryableStrategyGenerationException exception) {
                    observations.add(Observation.failure(
                            scenario.name(), run, elapsedMillis(startedNanos),
                            exception.getFailureCode()
                    ));
                }
            }
        }

        assertThat(observations).anyMatch(Observation::success);

        EvaluationReport report = new EvaluationReport(
                Instant.now(), properties.getModel(), properties.getThinkingLevel(),
                repetitions,
                List.copyOf(observations), summarize(observations)
        );
        Path reportPath = reportPath(
                properties.getThinkingLevel(), evaluationScenarioFilter()
        );
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                reportPath.toFile(), report
        );
        System.out.printf(
                "AI recommendation evaluation report: %s%n%s%n",
                reportPath.toAbsolutePath(),
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(report.summary())
        );
    }

    private static List<Scenario> scenarios() {
        return List.of(
                scenario(
                        "ECONOMIC_BALANCE", 900001L, false,
                        List.of(
                                spec("DISCOUNT-15", "DISCOUNT", StrategyType.PRICE_DISCOUNT,
                                        10L, "125000", 1, 1, "70", "5"),
                                spec("TRANSFER-20", "TRANSFER-20", StrategyType.RT_TRANSFER,
                                        20L, "118000", 2, 2, "68", "4"),
                                spec("REALLOCATE-30", "REALLOCATE-30",
                                        StrategyType.REALLOCATION,
                                        30L, "112000", 3, 3, "65", "6"),
                                spec("EXPAND-40", "EXPAND-40",
                                        StrategyType.CHANNEL_EXPANSION,
                                        40L, "101000", 4, 4, "72", "3"),
                                spec("CONCENTRATE-50", "CONCENTRATE-50",
                                        StrategyType.CHANNEL_CONCENTRATION,
                                        50L, "97000", 5, 5, "75", "2"),
                                spec("DISCOUNT-25", "DISCOUNT-25",
                                        StrategyType.PRICE_DISCOUNT,
                                        60L, "90000", 6, 6, "82", "1")
                        )
                ),
                scenario(
                        "USER_PRIORITY_TRADEOFF", 900002L, true,
                        List.of(
                                spec("PREFERRED-TRANSFER", "PREFERRED-TRANSFER",
                                        StrategyType.RT_TRANSFER,
                                        20L, "92000", 1, 1, "66", "4"),
                                spec("PREFERRED-DISCOUNT", "PREFERRED-DISCOUNT",
                                        StrategyType.PRICE_DISCOUNT,
                                        20L, "88000", 1, 1, "73", "2"),
                                spec("HIGH-EFFECT", "HIGH-EFFECT",
                                        StrategyType.PRICE_DISCOUNT,
                                        30L, "140000", 2, 2, "78", "1"),
                                spec("SECOND-TARGET", "SECOND-TARGET",
                                        StrategyType.REALLOCATION,
                                        30L, "126000", 2, 2, "70", "3"),
                                spec("EXPANSION", "EXPANSION",
                                        StrategyType.CHANNEL_EXPANSION,
                                        40L, "108000", 3, 3, "74", "2"),
                                spec("CONCENTRATION", "CONCENTRATION",
                                        StrategyType.CHANNEL_CONCENTRATION,
                                        50L, "103000", 4, 4, "76", "2")
                        )
                ),
                scenario(
                        "DISPOSAL_TRADEOFF", 900003L, false,
                        List.of(
                                spec("MAX-NET", "MAX-NET", StrategyType.PRICE_DISCOUNT,
                                        10L, "135000", 1, 1, "62", "12"),
                                spec("MIN-DISPOSAL", "MIN-DISPOSAL",
                                        StrategyType.RT_TRANSFER,
                                        20L, "116000", 2, 2, "80", "0"),
                                spec("BALANCED", "BALANCED",
                                        StrategyType.REALLOCATION,
                                        30L, "128000", 3, 3, "74", "3"),
                                spec("EXPAND", "EXPAND",
                                        StrategyType.CHANNEL_EXPANSION,
                                        40L, "109000", 4, 4, "77", "1"),
                                spec("CONCENTRATE", "CONCENTRATE",
                                        StrategyType.CHANNEL_CONCENTRATION,
                                        50L, "104000", 5, 5, "72", "4"),
                                spec("LOW-COST", "LOW-COST",
                                        StrategyType.PRICE_DISCOUNT,
                                        60L, "99000", 6, 6, "68", "6")
                        )
                )
        );
    }

    private static List<Scenario> selectedScenarios() {
        String filter = evaluationScenarioFilter();
        if (filter == null) {
            return scenarios();
        }
        List<Scenario> selected = scenarios().stream()
                .filter(scenario -> scenario.name().equals(filter))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI_RECOMMENDATION_EVAL_SCENARIO is unknown: " + filter
            );
        }
        return selected;
    }

    private static Scenario scenario(
            String name,
            long caseId,
            boolean userPriority,
            List<CandidateSpec> specs
    ) {
        List<AiRecommendationRequest.CandidateInput> candidates = specs.stream()
                .map(spec -> candidate(spec, userPriority))
                .toList();
        return new Scenario(name, new AiRecommendationRequest(
                "ai-strategy-recommendation-v4", caseId, 3, 4,
                new AiRecommendationRequest.BaselineInput(
                        decimal("50"), decimal("500000"), decimal("120000"),
                        decimal("0.24"), null, decimal("100"), decimal("15"),
                        decimal("45000"), decimal("12000")
                ),
                candidates
        ));
    }

    private static AiRecommendationRequest.CandidateInput candidate(
            CandidateSpec spec,
            boolean userPriority
    ) {
        AiRecommendationRequest.PrioritySource source = userPriority
                ? AiRecommendationRequest.PrioritySource.USER
                : AiRecommendationRequest.PrioritySource.AI_DEFAULT;
        BigDecimal revenue = decimal("800000");
        BigDecimal contributionMargin = decimal("210000");
        BigDecimal remaining = decimal("150").subtract(spec.salesQty());
        BigDecimal actionCost = spec.type() == StrategyType.RT_TRANSFER
                ? decimal("25000") : decimal("10000");
        BigDecimal strategyPrice = spec.type() == StrategyType.PRICE_DISCOUNT
                ? decimal("8500") : null;
        BigDecimal discountRate = spec.type() == StrategyType.PRICE_DISCOUNT
                ? decimal("0.15") : null;
        return new AiRecommendationRequest.CandidateInput(
                spec.id(), spec.family(), List.of(spec.type()),
                LocalDate.of(2026, 8, 29), LocalDate.of(2026, 9, 11),
                List.of(new AiRecommendationRequest.ActionInput(
                        spec.type(), 1L, 10L, 1L, spec.targetSalesPointId(),
                        decimal("100"), actionCost, strategyPrice, discountRate
                )),
                new AiRecommendationRequest.SummaryInput(
                        spec.salesQty(), revenue, contributionMargin,
                        decimal("0.2625"), 12, remaining, spec.disposalQty(),
                        decimal("15000"), decimal("6000"), actionCost,
                        spec.netEffect()
                ),
                new AiRecommendationRequest.ComparisonInput(
                        spec.salesQty().subtract(decimal("50")),
                        decimal("300000"), decimal("90000"),
                        decimal("100").subtract(remaining),
                        decimal("15").subtract(spec.disposalQty()),
                        decimal("30000"), decimal("6000"), spec.netEffect()
                ),
                List.of("DISCOUNT_DEMAND_UPLIFT_NOT_APPLIED"),
                new AiRecommendationRequest.PreferenceInput(
                        userPriority ? spec.strategyPriority() : null, source,
                        userPriority ? spec.targetPriority() : null, source,
                        70
                ),
                decimal("100")
        );
    }

    private static Summary summarize(List<Observation> observations) {
        List<Observation> successes = observations.stream()
                .filter(Observation::success).toList();
        List<Long> latency = successes.stream()
                .map(Observation::latencyMillis).sorted().toList();
        Map<String, Long> failuresByCode = observations.stream()
                .filter(value -> !value.success())
                .collect(java.util.stream.Collectors.groupingBy(
                        Observation::failureCode,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));
        return new Summary(
                observations.size(), successes.size(),
                observations.size() - successes.size(), failuresByCode,
                percentile(latency, 0.50),
                percentile(latency, 0.95),
                averageInteger(successes.stream().map(Observation::inputTokens).toList()),
                averageInteger(successes.stream().map(Observation::outputTokens).toList()),
                averageInteger(successes.stream().map(Observation::thoughtTokens).toList()),
                averageInteger(successes.stream().map(Observation::totalTokens).toList()),
                averageDecimal(successes.stream()
                        .map(value -> value.quality().top1RegretRate()).toList()),
                averageDecimal(successes.stream()
                        .map(value -> value.quality().familyDiversityRatio()).toList()),
                complianceRate(successes, true),
                complianceRate(successes, false),
                successes.stream().mapToInt(value ->
                        value.quality().structuralViolationCount()).sum(),
                top1Stability(successes)
        );
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private static BigDecimal averageInteger(List<Integer> values) {
        List<Integer> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(present.stream().mapToLong(Integer::longValue).sum())
                .divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageDecimal(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return present.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(present.size()), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal complianceRate(
            List<Observation> observations,
            boolean strategy
    ) {
        List<Boolean> values = observations.stream()
                .map(value -> strategy
                        ? value.quality().top1StrategyPriorityCompliant()
                        : value.quality().top1TargetPriorityCompliant())
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        long compliant = values.stream().filter(Boolean::booleanValue).count();
        return BigDecimal.valueOf(compliant)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private static Map<String, BigDecimal> top1Stability(
            List<Observation> observations
    ) {
        Map<String, List<Observation>> byScenario = observations.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Observation::scenario, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        byScenario.forEach((scenario, values) -> {
            Map<String, Long> counts = values.stream().collect(
                    java.util.stream.Collectors.groupingBy(
                            value -> value.rankedCandidateIds().get(0),
                            java.util.stream.Collectors.counting()
                    )
            );
            long mostFrequent = counts.values().stream()
                    .max(Long::compareTo).orElse(0L);
            result.put(scenario, BigDecimal.valueOf(mostFrequent)
                    .divide(BigDecimal.valueOf(values.size()), 6,
                            RoundingMode.HALF_UP));
        });
        return Map.copyOf(result);
    }

    private static List<String> rankedCandidateIds(
            AiRecommendationProviderResponse response
    ) {
        return response.recommendations().stream()
                .sorted(Comparator.comparingInt(
                        AiRecommendationProviderResponse.Recommendation::rank))
                .map(AiRecommendationProviderResponse.Recommendation::candidateId)
                .toList();
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static int repetitions() {
        String configured = System.getenv("AI_RECOMMENDATION_EVAL_RUNS");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_REPETITIONS;
        }
        int value = Integer.parseInt(configured);
        if (value <= 0 || value > 10) {
            throw new IllegalArgumentException(
                    "AI_RECOMMENDATION_EVAL_RUNS must be between 1 and 10"
            );
        }
        return value;
    }

    private static String evaluationThinkingLevel() {
        String configured = System.getenv("AI_RECOMMENDATION_EVAL_THINKING_LEVEL");
        return configured == null || configured.isBlank() ? "low" : configured;
    }

    private static String evaluationScenarioFilter() {
        String configured = System.getenv("AI_RECOMMENDATION_EVAL_SCENARIO");
        return configured == null || configured.isBlank() ? null : configured;
    }

    private static Path reportPath(String thinkingLevel, String scenarioFilter) {
        String suffix = scenarioFilter == null
                ? thinkingLevel
                : thinkingLevel + "-" + scenarioFilter.toLowerCase(
                        java.util.Locale.ROOT
                ).replace('_', '-');
        return Path.of(
                "build", "reports", "ai-strategy-evaluation",
                "gemini-quality-evaluation-" + suffix + ".json"
        );
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

    private static String environmentOrProperty(
            String environmentName,
            String propertyValue
    ) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null || environmentValue.isBlank()
                ? propertyValue : environmentValue;
    }

    private static String property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static CandidateSpec spec(
            String id,
            String family,
            StrategyType type,
            Long targetSalesPointId,
            String netEffect,
            int strategyPriority,
            int targetPriority,
            String salesQty,
            String disposalQty
    ) {
        return new CandidateSpec(
                id, family, type, targetSalesPointId, decimal(netEffect),
                strategyPriority, targetPriority, decimal(salesQty),
                decimal(disposalQty)
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record LocalSettings(String apiKey, String model) {
    }

    private record Scenario(String name, AiRecommendationRequest request) {
    }

    private record CandidateSpec(
            String id,
            String family,
            StrategyType type,
            Long targetSalesPointId,
            BigDecimal netEffect,
            int strategyPriority,
            int targetPriority,
            BigDecimal salesQty,
            BigDecimal disposalQty
    ) {
    }

    private record Observation(
            String scenario,
            int run,
            boolean success,
            long latencyMillis,
            Integer inputTokens,
            Integer outputTokens,
            Integer thoughtTokens,
            Integer totalTokens,
            List<String> rankedCandidateIds,
            AiRecommendationQualityEvaluation quality,
            String failureCode
    ) {
        private static Observation success(
                String scenario,
                int run,
                long latencyMillis,
                Integer inputTokens,
                Integer outputTokens,
                Integer thoughtTokens,
                Integer totalTokens,
                List<String> rankedCandidateIds,
                AiRecommendationQualityEvaluation quality
        ) {
            return new Observation(
                    scenario, run, true, latencyMillis, inputTokens, outputTokens,
                    thoughtTokens, totalTokens,
                    rankedCandidateIds, quality, null
            );
        }

        private static Observation failure(
                String scenario,
                int run,
                long latencyMillis,
                String failureCode
        ) {
            return new Observation(
                    scenario, run, false, latencyMillis, null, null, null, null,
                    List.of(), null, failureCode
            );
        }
    }

    private record EvaluationReport(
            Instant generatedAt,
            String model,
            String thinkingLevel,
            int repetitionsPerScenario,
            List<Observation> observations,
            Summary summary
    ) {
    }

    private record Summary(
            int totalAttempts,
            int successfulRuns,
            int failedRuns,
            Map<String, Long> failuresByCode,
            long latencyP50Millis,
            long latencyP95Millis,
            BigDecimal averageInputTokens,
            BigDecimal averageOutputTokens,
            BigDecimal averageThoughtTokens,
            BigDecimal averageTotalTokens,
            BigDecimal averageTop1RegretRate,
            BigDecimal averageFamilyDiversityRatio,
            BigDecimal top1StrategyPriorityComplianceRate,
            BigDecimal top1TargetPriorityComplianceRate,
            int structuralViolationCount,
            Map<String, BigDecimal> top1StabilityByScenario
    ) {
    }
}
