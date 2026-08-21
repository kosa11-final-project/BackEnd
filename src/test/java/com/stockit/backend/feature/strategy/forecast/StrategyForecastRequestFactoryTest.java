package com.stockit.backend.feature.strategy.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

@ExtendWith(MockitoExtension.class)
class StrategyForecastRequestFactoryTest {

    @Mock
    private StrategyCaseMapper strategyCaseMapper;

    private StrategyForecastRequestFactory factory;

    @BeforeEach
    void setUp() {
        factory = new StrategyForecastRequestFactory(
                strategyCaseMapper,
                new StrategyForecastRequestHasher(
                        new ObjectMapper().findAndRegisterModules()
                )
        );
    }

    @Test
    void keepsEmptyCandidatesInRequestAndUsesAllActivePointsAsExpectedScope() {
        when(strategyCaseMapper.selectAllActiveSalesPointIds())
                .thenReturn(List.of(30L, 10L, 20L));

        StrategyForecastRequestContext context = factory.create(
                strategyCase(null, 10L),
                payload(List.of())
        );

        assertThat(context.request().strategyRequestId()).isEqualTo(12345L);
        assertThat(context.request().candidateSalesPointIds()).isEmpty();
        assertThat(context.expectedSalesPointIds()).containsExactly(10L, 20L, 30L);
        assertThat(context.requestHash()).hasSize(64);
    }

    @Test
    void usesSourceAndCandidatesAsExactExpectedScope() {
        when(strategyCaseMapper.selectActiveSalesPointIds(List.of(20L, 10L)))
                .thenReturn(List.of(10L, 20L));

        StrategyForecastRequestContext context = factory.create(
                strategyCase(StrategyGenerationStage.FORECASTING, 10L),
                payload(List.of(20L))
        );

        assertThat(context.expectedSalesPointIds()).containsExactly(10L, 20L);
        assertThat(context.request().candidateSalesPointIds()).containsExactly(20L);
    }

    @Test
    void rejectsADeactivatedForecastTargetAtCurrentStage() {
        when(strategyCaseMapper.selectActiveSalesPointIds(List.of(20L, 10L)))
                .thenReturn(List.of(10L));

        assertThatThrownBy(() -> factory.create(
                strategyCase(StrategyGenerationStage.FORECASTING, 10L),
                payload(List.of(20L))
        )).isInstanceOfSatisfying(
                PermanentStrategyGenerationException.class,
                exception -> {
                    assertThat(exception.getFailureCode())
                            .isEqualTo("FORECAST_TARGET_INVALID");
                    assertThat(exception.getExpectedStage())
                            .isEqualTo(StrategyGenerationStage.FORECASTING);
                }
        );
    }

    @Test
    void rejectsForecastStartDateAfterEndDate() {
        assertThatThrownBy(() -> factory.create(
                strategyCase(StrategyGenerationStage.FORECASTING, 10L),
                payload(
                        List.of(20L),
                        LocalDate.of(2026, 8, 21),
                        LocalDate.of(2026, 8, 20)
                )
        )).isInstanceOfSatisfying(
                PermanentStrategyGenerationException.class,
                exception -> assertThat(exception.getFailureCode())
                        .isEqualTo("FORECAST_REQUEST_INVALID")
        );
    }

    @Test
    void rejectsEmptyActiveSalesPointScope() {
        when(strategyCaseMapper.selectAllActiveSalesPointIds())
                .thenReturn(List.of());

        assertThatThrownBy(() -> factory.create(
                strategyCase(StrategyGenerationStage.FORECASTING, null),
                payload(List.of())
        )).isInstanceOfSatisfying(
                PermanentStrategyGenerationException.class,
                exception -> assertThat(exception.getFailureCode())
                        .isEqualTo("FORECAST_TARGET_INVALID")
        );
    }

    @Test
    void rejectsSourceMissingFromAllActiveSalesPoints() {
        when(strategyCaseMapper.selectAllActiveSalesPointIds())
                .thenReturn(List.of(20L));

        assertThatThrownBy(() -> factory.create(
                strategyCase(StrategyGenerationStage.FORECASTING, 10L),
                payload(List.of())
        )).isInstanceOfSatisfying(
                PermanentStrategyGenerationException.class,
                exception -> assertThat(exception.getFailureCode())
                        .isEqualTo("FORECAST_TARGET_INVALID")
        );
    }

    @Test
    void splitsActiveSalesPointLookupAtOracleInLimit() {
        List<Long> candidates = LongStream.rangeClosed(1, 1_001)
                .boxed()
                .toList();
        List<Long> firstBatch = candidates.subList(0, 1_000);
        List<Long> secondBatch = candidates.subList(1_000, 1_001);
        when(strategyCaseMapper.selectActiveSalesPointIds(firstBatch))
                .thenReturn(firstBatch);
        when(strategyCaseMapper.selectActiveSalesPointIds(secondBatch))
                .thenReturn(secondBatch);

        StrategyForecastRequestContext context = factory.create(
                strategyCase(StrategyGenerationStage.FORECASTING, null),
                payload(candidates)
        );

        assertThat(context.expectedSalesPointIds()).containsExactlyElementsOf(candidates);
        verify(strategyCaseMapper).selectActiveSalesPointIds(firstBatch);
        verify(strategyCaseMapper).selectActiveSalesPointIds(secondBatch);
    }

    private static StrategyCaseRequestPayload payload(List<Long> candidates) {
        return payload(
                candidates,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21)
        );
    }

    private static StrategyCaseRequestPayload payload(
            List<Long> candidates,
            LocalDate forecastStartDate,
            LocalDate forecastEndDate
    ) {
        return new StrategyCaseRequestPayload(
                List.of(),
                candidates,
                List.of(),
                null,
                null,
                forecastStartDate,
                forecastEndDate
        );
    }

    private static StrategyCaseVO strategyCase(
            StrategyGenerationStage stage,
            Long sourceSalesPointId
    ) {
        StrategyCaseVO strategyCase = new StrategyCaseVO();
        strategyCase.setStrategyCaseId(12345L);
        strategyCase.setSkuId(1001L);
        strategyCase.setRequestedSalesPointId(sourceSalesPointId);
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATING);
        strategyCase.setGenerationStage(stage);
        return strategyCase;
    }
}
