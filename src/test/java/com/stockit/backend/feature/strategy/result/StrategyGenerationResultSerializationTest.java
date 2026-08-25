package com.stockit.backend.feature.strategy.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateAssumption;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;

class StrategyGenerationResultSerializationTest {

    @Test
    void roundTripsRedisResultIncludingDailySeriesAndActions() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        StrategyGenerationResult original = result();

        StrategyGenerationResult restored = mapper.readValue(
                mapper.writeValueAsString(original), StrategyGenerationResult.class
        );

        assertThat(restored).isEqualTo(original);
        assertThat(restored.options().get(0).simulation().dailySeries()).hasSize(1);
        assertThat(restored.options().get(0).candidate().actions()).hasSize(1);
    }

    @Test
    void roundTripsNoRecommendationResultWithoutProviderCall() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        StrategyGenerationResult original = new StrategyGenerationResult(
                StrategyGenerationResult.CURRENT_SCHEMA_VERSION,
                2L,
                LocalDateTime.of(2026, 8, 24, 10, 0),
                result().baselineSimulation(),
                List.of(),
                new StrategyGenerationResult.NoRecommendation(
                        "CURRENT_STATE_PREFERRED",
                        "현재 상태 유지가 유리합니다."
                ),
                null
        );

        StrategyGenerationResult restored = mapper.readValue(
                mapper.writeValueAsString(original), StrategyGenerationResult.class
        );

        assertThat(restored).isEqualTo(original);
        assertThat(restored.options()).isEmpty();
        assertThat(restored.providerMetadata()).isNull();
    }

    private static StrategyGenerationResult result() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        BaselineSimulation baseline = new BaselineSimulation(
                new BaselineSimulation.Summary(
                        d("1"), d("100"), d("30"), d("0.3"), null,
                        d("9"), d("1")
                ),
                List.of(new BaselineSimulation.DailyPoint(
                        date, d("1"), d("9"), d("100"), d("30")
                ))
        );
        StrategyCandidateSimulation simulation = new StrategyCandidateSimulation(
                "C1",
                new StrategyCandidateSimulation.Summary(
                        d("2"), d("180"), d("50"), d("0.2778"), 5,
                        d("8"), d("0"), d("10"), d("40")
                ),
                new StrategyCandidateSimulation.ComparisonToBaseline(
                        d("1"), d("80"), d("20"), d("1"), d("1"), d("10")
                ),
                List.of(new StrategyCandidateSimulation.DailyPoint(
                        date, d("2"), d("8"), d("180"), d("50")
                )),
                List.of(CandidateAssumption.DISCOUNT_DEMAND_UPLIFT_NOT_APPLIED)
        );
        StrategyGenerationResult.Candidate candidate =
                new StrategyGenerationResult.Candidate(
                        "C1", List.of(StrategyType.PRICE_DISCOUNT), date, date.plusDays(4),
                        List.of(new StrategyGenerationResult.Action(
                                StrategyType.PRICE_DISCOUNT, 1L, 10L, 1L, 10L,
                                d("10"), d("0"), d("90"), d("0.1"),
                                List.of(new StrategyGenerationResult.LotAllocation(
                                        1L, 1L, d("10"), 1
                                ))
                        )),
                        List.of(CandidateAssumption.DISCOUNT_DEMAND_UPLIFT_NOT_APPLIED),
                        new StrategyGenerationResult.Preference(1, 1, 100), d("10")
                );
        return new StrategyGenerationResult(
                1, 1L, LocalDateTime.of(2026, 8, 24, 10, 0), baseline,
                List.of(new StrategyGenerationResult.Option(
                        1, "할인 전략", "추천 이유", "장점", "주의사항",
                        candidate, simulation
                )),
                null,
                new StrategyGenerationResult.ProviderMetadata(
                        null, "gemini-3.7-flash", 100, 50
                )
        );
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }
}
