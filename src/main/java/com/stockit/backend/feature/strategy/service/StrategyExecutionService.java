package com.stockit.backend.feature.strategy.service;

import java.util.List;

import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionResponse;

public interface StrategyExecutionService {
    List<StrategyExecutionResponse> findAll();

    StrategyExecutionResponse findByStrategyCaseId(Long strategyCaseId);
}
