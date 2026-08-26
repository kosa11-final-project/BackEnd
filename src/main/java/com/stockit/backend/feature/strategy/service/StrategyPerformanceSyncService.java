package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.dto.response.StrategyPerformanceSyncResponse;

public interface StrategyPerformanceSyncService {
    StrategyPerformanceSyncResponse synchronize(Long requestedBy);
}
