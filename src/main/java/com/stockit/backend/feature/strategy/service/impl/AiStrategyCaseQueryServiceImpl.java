package com.stockit.backend.feature.strategy.service.impl;

import org.springframework.stereotype.Service;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseResponse;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.result.InvalidStrategyResultException;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.result.StrategyResultStoreException;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseQueryService;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

@Service
public class AiStrategyCaseQueryServiceImpl implements AiStrategyCaseQueryService {

    private final StrategyCaseMapper caseMapper;
    private final StrategyResultStore resultStore;

    public AiStrategyCaseQueryServiceImpl(
            StrategyCaseMapper caseMapper,
            StrategyResultStore resultStore
    ) {
        this.caseMapper = caseMapper;
        this.resultStore = resultStore;
    }

    @Override
    public AiStrategyCaseResponse find(Long strategyCaseId) {
        if (strategyCaseId == null || strategyCaseId <= 0) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        StrategyCaseVO strategyCase = caseMapper.selectStrategyCaseById(strategyCaseId);
        if (strategyCase == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        StrategyGenerationResult result = null;
        if (strategyCase.getResultCacheKey() != null) {
            try {
                result = resultStore.find(strategyCaseId).orElse(null);
            } catch (InvalidStrategyResultException | StrategyResultStoreException exception) {
                throw exception;
            }
        }
        return AiStrategyCaseResponse.from(strategyCase, result);
    }
}
