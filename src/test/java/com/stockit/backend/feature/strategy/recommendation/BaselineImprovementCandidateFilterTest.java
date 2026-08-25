package com.stockit.backend.feature.strategy.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;

class BaselineImprovementCandidateFilterTest {

    private final BaselineImprovementCandidateFilter filter =
            new BaselineImprovementCandidateFilter();

    @Test
    void removesCandidateThatImprovesNoMeasuredOutcome() {
        var candidate = evaluated("LOSS", "0", "0", "0", "-275");

        assertThat(filter.filter(List.of(candidate))).isEmpty();
    }

    @Test
    void keepsTradeoffCandidateWhenDisposalIsReducedDespiteNegativeNetEffect() {
        var candidate = evaluated("TRADEOFF", "0", "0", "30", "-10000");

        assertThat(filter.filter(List.of(candidate))).containsExactly(candidate);
    }

    @Test
    void keepsCandidateWhenAnyMeasuredOutcomeIsPositive() {
        var sales = evaluated("SALES", "1", "0", "0", "-1");
        var remaining = evaluated("REMAINING", "0", "1", "0", "-1");
        var netEffect = evaluated("NET", "0", "0", "0", "1");

        assertThat(filter.filter(List.of(sales, remaining, netEffect)))
                .containsExactly(sales, remaining, netEffect);
    }

    private static StrategyCandidateEvaluationResult.EvaluatedCandidate evaluated(
            String id,
            String salesDelta,
            String remainingReduction,
            String disposalReduction,
            String netEffect
    ) {
        StrategyCandidate candidate = mock(StrategyCandidate.class);
        StrategyCandidateSimulation simulation = mock(StrategyCandidateSimulation.class);
        when(candidate.candidateId()).thenReturn(id);
        when(simulation.candidateId()).thenReturn(id);
        when(simulation.comparisonToBaseline()).thenReturn(
                new StrategyCandidateSimulation.ComparisonToBaseline(
                        decimal(salesDelta), BigDecimal.ZERO, BigDecimal.ZERO,
                        decimal(remainingReduction), decimal(disposalReduction),
                        decimal(netEffect)
                )
        );
        return new StrategyCandidateEvaluationResult.EvaluatedCandidate(
                candidate, simulation
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
