package com.stockit.backend.feature.strategy.service;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyRecommendationOutcome;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStateChangedEvent;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;

/**
 * 외부 API 및 Redis 작업과 분리된 짧은 전략 생성 단계 트랜잭션 서비스
 *
 * <p>느린 외부 작업 동안 DB 트랜잭션과 잠금을 유지하지 않고, 기대 상태를 만족하는
 * Case만 원자적으로 전환</p>
 */
@Service
public class StrategyGenerationStageService {

    private final StrategyCaseMapper strategyCaseMapper;
    private final ApplicationEventPublisher eventPublisher;

    public StrategyGenerationStageService(
            StrategyCaseMapper strategyCaseMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 아직 Worker가 시작하지 않은 생성 Case만 FORECASTING으로 선점
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enterForecasting(Long strategyCaseId) {
        boolean updated = strategyCaseMapper.markForecastingIfPending(
                strategyCaseId
        ) == 1;
        if (updated) {
            publish(
                    strategyCaseId,
                    StrategyCaseStatus.GENERATING,
                    StrategyGenerationStage.FORECASTING
            );
        }
        return updated;
    }

    /**
     * 예측 체크포인트 저장을 마친 FORECASTING Case만 다음 단계로 전환
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeForecasting(Long strategyCaseId) {
        boolean updated = strategyCaseMapper.markStrategyGeneratingIfForecasting(
                strategyCaseId
        ) == 1;
        if (updated) {
            publish(
                    strategyCaseId,
                    StrategyCaseStatus.GENERATING,
                    StrategyGenerationStage.STRATEGY_GENERATING
            );
        }
        return updated;
    }

    /** Redis 최종 결과가 확정된 Case만 생성 완료로 전환한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeStrategyGeneration(
            Long strategyCaseId,
            String resultCacheKey,
            LocalDateTime resultExpiresAt,
            StrategyRecommendationOutcome recommendationOutcome
    ) {
        boolean updated = strategyCaseMapper.markGeneratedIfStrategyGenerating(
                strategyCaseId, resultCacheKey, resultExpiresAt, recommendationOutcome
        ) == 1;
        if (updated) {
            publish(
                    strategyCaseId,
                    StrategyCaseStatus.GENERATED,
                    StrategyGenerationStage.COMPARISON_READY
            );
        }
        return updated;
    }

    private void publish(
            Long strategyCaseId,
            StrategyCaseStatus caseStatus,
            StrategyGenerationStage generationStage
    ) {
        eventPublisher.publishEvent(new StrategyGenerationStateChangedEvent(
                strategyCaseId,
                caseStatus,
                generationStage
        ));
    }
}
