package com.stockit.backend.feature.strategy.result;

import java.util.Optional;

public interface StrategyResultLockManager {
    Optional<StrategyResultLock> tryAcquire(Long strategyCaseId);
    void release(StrategyResultLock lock);
}
