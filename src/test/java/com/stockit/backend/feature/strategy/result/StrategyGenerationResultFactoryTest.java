package com.stockit.backend.feature.strategy.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.engine.StrategyCandidateSimulationEngine;
import com.stockit.backend.feature.strategy.recommendation.StrategyRecommendationResult;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

class StrategyGenerationResultFactoryTest {

    @Test
    void createsCacheableNoRecommendationResultWithoutDailyCandidateSimulation() {
        StrategyCandidateSimulationEngine simulationEngine = mock(
                StrategyCandidateSimulationEngine.class
        );
        StrategyDateTimeProvider dateTimeProvider = mock(StrategyDateTimeProvider.class);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 24, 12, 0);
        when(dateTimeProvider.now()).thenReturn(generatedAt);
        BaselineSimulation baseline = mock(BaselineSimulation.class);
        StrategyRecommendationResult recommendation =
                StrategyRecommendationResult.noRecommendation(
                        1L,
                        mock(StrategyCalculationContext.class),
                        baseline,
                        "CURRENT_STATE_PREFERRED",
                        "현재 상태 유지가 유리합니다."
                );
        StrategyGenerationResultFactory factory = new StrategyGenerationResultFactory(
                simulationEngine, dateTimeProvider
        );

        StrategyGenerationResult result = factory.create(1L, recommendation);

        assertThat(result.generatedAt()).isEqualTo(generatedAt);
        assertThat(result.baselineSimulation()).isSameAs(baseline);
        assertThat(result.options()).isEmpty();
        assertThat(result.noRecommendation().code())
                .isEqualTo("CURRENT_STATE_PREFERRED");
        assertThat(result.providerMetadata()).isNull();
        verify(simulationEngine, never()).simulate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
