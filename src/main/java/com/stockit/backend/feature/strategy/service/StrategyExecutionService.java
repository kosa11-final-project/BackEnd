package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionPageResponse;
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionResponse;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionQuery;

public interface StrategyExecutionService {
    StrategyExecutionPageResponse findAll(StrategyExecutionQuery query);

    StrategyExecutionResponse findByStrategyCaseId(Long strategyCaseId);
}
