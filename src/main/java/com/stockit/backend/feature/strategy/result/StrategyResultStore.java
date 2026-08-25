package com.stockit.backend.feature.strategy.result;

import java.util.Optional;

public interface StrategyResultStore {
    Optional<StrategyGenerationResult> find(Long strategyCaseId);
    StrategyResultCacheEntry save(StrategyGenerationResult result);
}
