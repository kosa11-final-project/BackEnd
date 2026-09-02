package com.stockit.backend.feature.strategy.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SourceInventoryCapacityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.TargetAdditionalDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.engine.BaselineSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.engine.StrategyCandidateSimulationEngine;
import com.stockit.backend.feature.strategy.calculation.policy.DiscountDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.policy.DiscountSimulationProperties;
import com.stockit.backend.feature.strategy.calculation.policy.SalesPointDiscountPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/**
 * 외부 DB, ML, Gemini의 변동을 제외하고 후보 시뮬레이션 계산량만 비교하는 수동 벤치마크.
 *
 * <p>실행: {@code ./gradlew strategyPerfTest --info}</p>
 */
class StrategyCandidateSimulationBenchmarkTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 20);
    private static final LocalDate END = START.plusDays(89);
    private static final int WARMUP_ITERATIONS = Integer.getInteger(
            "strategyPerf.warmups", 10
    );
    private static final int MEASURE_ITERATIONS = Integer.getInteger(
            "strategyPerf.iterations", 30
    );

    private final BaselineSimulationEngine baselineEngine =
            new BaselineSimulationEngine();
    private final StrategyCandidateSimulationEngine candidateEngine = candidateEngine();

    @Test
    void benchmarkSmallMediumAndLargeCandidateSets() {
        List<Scenario> scenarios = List.of(
                new Scenario("small", 1, List.of(0), 5, 5, "5507.72"),
                new Scenario(
                        "medium", 5, List.of(0, 7, 14), 10, 20, "301083.00"
                ),
                new Scenario(
                        "large", 10, List.of(0, 7, 14, 21, 30), 10, 50,
                        "999540.00"
                )
        );

        for (Scenario scenario : scenarios) {
            BenchmarkInput input = input(scenario);
            for (int index = 0; index < WARMUP_ITERATIONS; index++) {
                runOnce(input);
            }

            List<Long> elapsedNanos = new ArrayList<>();
            BigDecimal checksum = BigDecimal.ZERO;
            for (int index = 0; index < MEASURE_ITERATIONS; index++) {
                long startedNanos = System.nanoTime();
                checksum = checksum.add(runOnce(input));
                elapsedNanos.add(System.nanoTime() - startedNanos);
            }

            Collections.sort(elapsedNanos);
            double p50Millis = percentileMillis(elapsedNanos, 0.50);
            double p95Millis = percentileMillis(elapsedNanos, 0.95);
            BigDecimal perRunChecksum = checksum.divide(
                    BigDecimal.valueOf(MEASURE_ITERATIONS)
            );
            System.out.printf(
                    "STRATEGY_PERF scenario=%s candidates=%d lots=%d "
                            + "forecastDays=90 p50Ms=%.3f p95Ms=%.3f checksum=%s%n",
                    scenario.name(), input.candidates().size(), scenario.lotCount(),
                    p50Millis, p95Millis, perRunChecksum.toPlainString()
            );
            assertThat(checksum).isNotNull();
            assertThat(perRunChecksum)
                    .as("candidate simulation output checksum for %s", scenario.name())
                    .isEqualByComparingTo(scenario.expectedChecksum());
        }
    }

    private BigDecimal runOnce(BenchmarkInput input) {
        BigDecimal checksum = BigDecimal.ZERO;
        for (StrategyCandidate candidate : input.candidates()) {
            checksum = checksum.add(candidateEngine.simulate(
                    input.context(),
                    candidate,
                    input.baseline(),
                    SimulationDetailLevel.SUMMARY_ONLY
            ).summary().netEffect());
        }
        return checksum;
    }

    private BenchmarkInput input(Scenario scenario) {
        StrategyCalculationContext context = context(scenario);
        return new BenchmarkInput(
                context,
                baselineEngine.simulate(context),
                candidates(scenario)
        );
    }

    private static StrategyCalculationContext context(Scenario scenario) {
        Map<Long, StrategyCalculationContext.SalesPoint> salesPoints =
                new LinkedHashMap<>();
        salesPoints.put(10L, salesPoint(10L, "2"));
        for (int index = 0; index < scenario.targetCount(); index++) {
            long salesPointId = 20L + index;
            salesPoints.put(salesPointId, salesPoint(salesPointId, "4"));
        }

        List<StrategyCalculationContext.InventoryLot> inventory = new ArrayList<>();
        for (int index = 0; index < scenario.lotCount(); index++) {
            inventory.add(new StrategyCalculationContext.InventoryLot(
                    1L + index,
                    1001L + index,
                    501L,
                    10L,
                    10L,
                    decimal("1000"),
                    BigDecimal.ZERO,
                    null,
                    START.minusDays(60L - index),
                    index % 3 == 0 ? END.minusDays(index % 20) : null,
                    null,
                    "AVAILABLE"
            ));
        }

        return new StrategyCalculationContext(
                12345L,
                10L,
                LocalDateTime.of(2026, 8, 20, 10, 0),
                START,
                END,
                new StrategyCalculationContext.Sku(
                        101L, "SKU-PERF", "성능 측정 SKU", "EA", BigDecimal.ONE
                ),
                decimal("50"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(), List.of(), null, null
                ),
                inventory,
                inventory,
                List.of(new StrategyCalculationContext.InventoryPolicy(
                        1L,
                        501L,
                        null,
                        null,
                        BigDecimal.ZERO,
                        null,
                        decimal("0.02"),
                        decimal("5")
                )),
                salesPoints,
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-perf-1",
                        1L,
                        OffsetDateTime.of(
                                2026, 8, 20, 9, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private static StrategyCalculationContext.SalesPoint salesPoint(
            long salesPointId,
            String dailyForecast
    ) {
        return new StrategyCalculationContext.SalesPoint(
                salesPointId,
                "SP-" + salesPointId,
                "판매처 " + salesPointId,
                BigDecimal.ZERO,
                true,
                new StrategyCalculationContext.Price(
                        1000L + salesPointId,
                        decimal("120"),
                        decimal("100"),
                        decimal("70"),
                        decimal("5"),
                        decimal("10")
                ),
                forecast(dailyForecast),
                List.of(new StrategyCalculationContext.WarehouseRoute(
                        2000L + salesPointId,
                        salesPointId,
                        501L,
                        1,
                        null
                ))
        );
    }

    private static List<StrategyCandidate> candidates(Scenario scenario) {
        List<StrategyCandidate> candidates = new ArrayList<>();
        for (int targetIndex = 0; targetIndex < scenario.targetCount(); targetIndex++) {
            long targetSalesPointId = 20L + targetIndex;
            for (int startOffset : scenario.startOffsets()) {
                for (int tier = 1; tier <= scenario.quantityTierCount(); tier++) {
                    BigDecimal quantity = BigDecimal.valueOf(tier * 10L);
                    int quantityPercentage = tier * 10;
                    candidates.add(new StrategyCandidate(
                            "PERF-" + targetSalesPointId + "-" + startOffset
                                    + "-" + quantityPercentage,
                            List.of(StrategyType.REALLOCATION),
                            START.plusDays(startOffset),
                            null,
                            List.of(new StrategyCandidate.Action(
                                    StrategyType.REALLOCATION,
                                    new StrategyCandidate.Location(501L, 10L),
                                    new StrategyCandidate.Location(
                                            501L, targetSalesPointId
                                    ),
                                    quantity,
                                    BigDecimal.ZERO,
                                    List.of(new StrategyCandidate.LotAllocation(
                                            1L, 1001L, quantity, 1
                                    ))
                            )),
                            List.of(),
                            new StrategyCandidate.Preference(
                                    1, targetIndex + 1, quantityPercentage
                            ),
                            new StrategyCandidate.MovementEvidence(
                                    decimal("100"),
                                    decimal("100"),
                                    decimal("100"),
                                    decimal("100")
                            )
                    ));
                }
            }
        }
        return List.copyOf(candidates);
    }

    private static Map<LocalDate, BigDecimal> forecast(String dailyQuantity) {
        Map<LocalDate, BigDecimal> values = new LinkedHashMap<>();
        for (LocalDate date = START; !date.isAfter(END); date = date.plusDays(1)) {
            values.put(date, decimal(dailyQuantity));
        }
        return values;
    }

    private static StrategyCandidateSimulationEngine candidateEngine() {
        DiscountSimulationProperties properties = new DiscountSimulationProperties();
        return new StrategyCandidateSimulationEngine(
                new TargetAdditionalDemandPolicy(),
                new SourceInventoryCapacityPolicy(),
                new DiscountDemandPolicy(
                        properties,
                        new SalesPointDiscountPolicy(properties)
                )
        );
    }

    private static double percentileMillis(List<Long> sortedNanos, double percentile) {
        int index = Math.max(
                0,
                (int) Math.ceil(percentile * sortedNanos.size()) - 1
        );
        return sortedNanos.get(index) / 1_000_000.0;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record Scenario(
            String name,
            int targetCount,
            List<Integer> startOffsets,
            int quantityTierCount,
            int lotCount,
            String expectedChecksum
    ) {
    }

    private record BenchmarkInput(
            StrategyCalculationContext context,
            BaselineSimulation baseline,
            List<StrategyCandidate> candidates
    ) {
    }
}
