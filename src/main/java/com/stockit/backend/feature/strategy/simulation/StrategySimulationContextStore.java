package com.stockit.backend.feature.strategy.simulation;

import java.util.Optional;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

/** 생성 당시 계산 스냅샷을 조정 시뮬레이션 TTL 동안 보관한다. */
public interface StrategySimulationContextStore {
    Optional<StrategyCalculationContext> find(Long strategyCaseId);
    void save(StrategyCalculationContext context);
}
