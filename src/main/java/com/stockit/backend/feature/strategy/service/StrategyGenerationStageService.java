package com.stockit.backend.feature.strategy.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    public StrategyGenerationStageService(StrategyCaseMapper strategyCaseMapper) {
        this.strategyCaseMapper = strategyCaseMapper;
    }

    /**
     * 아직 Worker가 시작하지 않은 생성 Case만 FORECASTING으로 선점
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enterForecasting(Long strategyCaseId) {
        return strategyCaseMapper.markForecastingIfPending(strategyCaseId) == 1;
    }

    /**
     * 예측 체크포인트 저장을 마친 FORECASTING Case만 다음 단계로 전환
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeForecasting(Long strategyCaseId) {
        return strategyCaseMapper.markStrategyGeneratingIfForecasting(
                strategyCaseId
        ) == 1;
    }
}
