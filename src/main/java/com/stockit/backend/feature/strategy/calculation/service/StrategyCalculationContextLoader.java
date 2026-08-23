package com.stockit.backend.feature.strategy.calculation.service;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

/** 저장된 Case와 수요예측 체크포인트를 계산 가능한 불변 입력으로 복원한다. */
public interface StrategyCalculationContextLoader {
    StrategyCalculationContext load(Long strategyCaseId);
}
