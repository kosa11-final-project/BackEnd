package com.stockit.backend.feature.strategy.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;

/**
 * 외부 API 호출과 분리된 짧은 전략 생성 단계 트랜잭션
 */
@Service
public class StrategyGenerationStageService {

    private final StrategyCaseMapper strategyCaseMapper;

    public StrategyGenerationStageService(StrategyCaseMapper strategyCaseMapper) {
        this.strategyCaseMapper = strategyCaseMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean enterForecasting(Long strategyCaseId) {
        return strategyCaseMapper.markForecastingIfPending(strategyCaseId) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean completeForecasting(Long strategyCaseId) {
        return strategyCaseMapper.markStrategyGeneratingIfForecasting(
                strategyCaseId
        ) == 1;
    }
}
