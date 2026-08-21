package com.stockit.backend.feature.strategy.forecast;

import java.util.Optional;

public interface ForecastLockManager {

    Optional<ForecastLock> tryAcquire(Long strategyCaseId);

    void release(ForecastLock lock);
}
