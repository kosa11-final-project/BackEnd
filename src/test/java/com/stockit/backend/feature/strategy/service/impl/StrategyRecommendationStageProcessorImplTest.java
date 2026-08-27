package com.stockit.backend.feature.strategy.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateEvaluationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.service.StrategyCandidateEvaluationService;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyRecommendationOutcome;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.recommendation.StrategyRecommendationResult;
import com.stockit.backend.feature.strategy.recommendation.StrategyRecommendationService;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResultFactory;
import com.stockit.backend.feature.strategy.result.StrategyResultCacheEntry;
import com.stockit.backend.feature.strategy.result.StrategyResultLock;
import com.stockit.backend.feature.strategy.result.StrategyResultLockManager;
import com.stockit.backend.feature.strategy.result.StrategyResultProperties;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.service.StrategyGenerationStageService;
import com.stockit.backend.feature.strategy.simulation.StrategySimulationContextStore;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

@ExtendWith(MockitoExtension.class)
class StrategyRecommendationStageProcessorImplTest {

    @Mock private StrategyCaseMapper caseMapper;
    @Mock private StrategyCandidateEvaluationService evaluationService;
    @Mock private StrategyRecommendationService recommendationService;
    @Mock private StrategyGenerationResultFactory resultFactory;
    @Mock private StrategyResultStore resultStore;
    @Mock private StrategyResultLockManager lockManager;
    @Mock private StrategyGenerationStageService stageService;
    @Mock private StrategySimulationContextStore simulationContextStore;

    private StrategyRecommendationStageProcessorImpl processor;

    @BeforeEach
    void setUp() {
        processor = new StrategyRecommendationStageProcessorImpl(
                caseMapper, evaluationService, recommendationService, resultFactory,
                resultStore, lockManager, stageService, new StrategyResultProperties(),
                simulationContextStore
        );
    }

    @Test
    void calculatesRecommendsCachesAndCompletesCase() {
        StrategyCaseVO generating = generatingCase();
        StrategyCandidateEvaluationResult evaluation = mock(
                StrategyCandidateEvaluationResult.class
        );
        StrategyRecommendationResult recommendation = mock(
                StrategyRecommendationResult.class
        );
        StrategyCalculationContext calculationContext = mock(
                StrategyCalculationContext.class
        );
        StrategyGenerationResult result = mock(StrategyGenerationResult.class);
        StrategyResultLock lock = new StrategyResultLock("lock", "owner");
        StrategyResultCacheEntry entry = new StrategyResultCacheEntry(
                "ai-strategy:case:1:result:v1",
                LocalDateTime.of(2026, 8, 27, 0, 0)
        );
        when(caseMapper.selectStrategyCaseById(1L)).thenReturn(generating, generating);
        when(resultStore.find(1L)).thenReturn(Optional.empty(), Optional.empty());
        when(lockManager.tryAcquire(1L)).thenReturn(Optional.of(lock));
        when(evaluationService.evaluate(1L, SimulationDetailLevel.SUMMARY_ONLY))
                .thenReturn(evaluation);
        when(recommendationService.recommend(1L, evaluation)).thenReturn(recommendation);
        when(recommendation.calculationContext()).thenReturn(calculationContext);
        when(resultFactory.create(1L, recommendation)).thenReturn(result);
        when(result.options()).thenReturn(List.of(mock(StrategyGenerationResult.Option.class)));
        when(resultStore.save(result)).thenReturn(entry);
        when(stageService.completeStrategyGeneration(
                1L, entry.cacheKey(), entry.expiresAt(),
                StrategyRecommendationOutcome.OPTIONS_GENERATED)).thenReturn(true);

        processor.process(1L);

        verify(resultStore).save(result);
        verify(simulationContextStore).save(calculationContext);
        verify(stageService).completeStrategyGeneration(
                1L, entry.cacheKey(), entry.expiresAt(),
                StrategyRecommendationOutcome.OPTIONS_GENERATED);
        verify(lockManager).release(lock);
    }

    @Test
    void resumesDbTransitionFromCachedResultWithoutCallingGemini() {
        StrategyCaseVO generating = generatingCase();
        StrategyGenerationResult cached = mock(StrategyGenerationResult.class);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 24, 0, 0);
        when(cached.generatedAt()).thenReturn(generatedAt);
        when(cached.options()).thenReturn(List.of());
        when(caseMapper.selectStrategyCaseById(1L)).thenReturn(generating);
        when(resultStore.find(1L)).thenReturn(Optional.of(cached));
        when(stageService.completeStrategyGeneration(any(), any(), any(), any()))
                .thenReturn(true);

        processor.process(1L);

        verify(evaluationService, never()).evaluate(any(), any());
        verify(recommendationService, never()).recommend(any(), any());
        verify(lockManager, never()).tryAcquire(any());
        verify(stageService).completeStrategyGeneration(
                any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(
                        StrategyRecommendationOutcome.MAINTAIN_CURRENT_STATE
                )
        );
    }

    private static StrategyCaseVO generatingCase() {
        StrategyCaseVO value = new StrategyCaseVO();
        value.setStrategyCaseId(1L);
        value.setCaseStatus(StrategyCaseStatus.GENERATING);
        value.setGenerationStage(StrategyGenerationStage.STRATEGY_GENERATING);
        return value;
    }
}
