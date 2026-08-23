package com.stockit.backend.feature.strategy.calculation.candidate.service;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;

/** 현재 등록된 전략 계산기만 실행해 메모리 내 후보군을 만든다. */
public interface StrategyCandidateGenerationService {

    CandidateGenerationResult generate(StrategyCalculationContext context);
}
