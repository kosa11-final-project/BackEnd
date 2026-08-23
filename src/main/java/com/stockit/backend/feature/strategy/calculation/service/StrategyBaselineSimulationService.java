package com.stockit.backend.feature.strategy.calculation.service;

import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;

/** 전략 Case의 계산 입력을 복원하고 무전략 기준 결과를 생성하는 진입점. */
public interface StrategyBaselineSimulationService {
    BaselineSimulation simulate(Long strategyCaseId);
}
