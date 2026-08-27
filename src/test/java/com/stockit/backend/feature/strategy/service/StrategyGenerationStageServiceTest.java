package com.stockit.backend.feature.strategy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStateChangedEvent;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;

@ExtendWith(MockitoExtension.class)
class StrategyGenerationStageServiceTest {

    @Mock private StrategyCaseMapper strategyCaseMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void publishesSilentProgressOnlyWhenForecastingTransitionSucceeds() {
        StrategyGenerationStageService service = service();
        when(strategyCaseMapper.markForecastingIfPending(101L)).thenReturn(1);

        assertThat(service.enterForecasting(101L)).isTrue();

        verify(eventPublisher).publishEvent(new StrategyGenerationStateChangedEvent(
                101L,
                StrategyCaseStatus.GENERATING,
                StrategyGenerationStage.FORECASTING
        ));
    }

    @Test
    void publishesSilentProgressOnlyWhenStrategyGeneratingTransitionSucceeds() {
        StrategyGenerationStageService service = service();
        when(strategyCaseMapper.markStrategyGeneratingIfForecasting(102L))
                .thenReturn(1);

        assertThat(service.completeForecasting(102L)).isTrue();

        verify(eventPublisher).publishEvent(new StrategyGenerationStateChangedEvent(
                102L,
                StrategyCaseStatus.GENERATING,
                StrategyGenerationStage.STRATEGY_GENERATING
        ));
    }

    @Test
    void publishesCompletionOnlyWhenGeneratedTransitionSucceeds() {
        StrategyGenerationStageService service = service();
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 29, 10, 0);
        when(strategyCaseMapper.markGeneratedIfStrategyGenerating(
                103L,
                "ai-strategy:case:103:result:v2",
                expiresAt
        )).thenReturn(1);

        assertThat(service.completeStrategyGeneration(
                103L,
                "ai-strategy:case:103:result:v2",
                expiresAt
        )).isTrue();

        verify(eventPublisher).publishEvent(new StrategyGenerationStateChangedEvent(
                103L,
                StrategyCaseStatus.GENERATED,
                StrategyGenerationStage.COMPARISON_READY
        ));
    }

    @Test
    void doesNotPublishWhenConditionalTransitionDoesNotChangeState() {
        StrategyGenerationStageService service = service();
        when(strategyCaseMapper.markForecastingIfPending(104L)).thenReturn(0);

        assertThat(service.enterForecasting(104L)).isFalse();

        verifyNoInteractions(eventPublisher);
    }

    private StrategyGenerationStageService service() {
        return new StrategyGenerationStageService(
                strategyCaseMapper,
                eventPublisher
        );
    }
}
