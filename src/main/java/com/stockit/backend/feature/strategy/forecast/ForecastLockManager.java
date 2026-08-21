package com.stockit.backend.feature.strategy.forecast;

import java.util.Optional;

/**
 * 동일 전략 Case의 수요예측 실행권을 조정하는 분산 Lock Port
 */
public interface ForecastLockManager {

    /**
     * 다른 Worker가 소유하지 않은 경우에만 제한된 실행권 획득
     */
    Optional<ForecastLock> tryAcquire(Long strategyCaseId);

    /**
     * 현재 Worker가 소유한 실행권 해제
     */
    void release(ForecastLock lock);
}
