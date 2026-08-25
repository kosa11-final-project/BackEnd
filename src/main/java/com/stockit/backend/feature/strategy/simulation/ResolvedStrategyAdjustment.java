package com.stockit.backend.feature.strategy.simulation;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy.PeriodConstraints;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** 조정 화면 응답과 최종 선택 영속화가 공유하는 서버 재계산 결과. */
public record ResolvedStrategyAdjustment(
        StrategyGenerationResult generationResult,
        StrategyGenerationResult.Option originalOption,
        StrategyCalculationContext adjustedContext,
        StrategyCandidate adjustedCandidate,
        PeriodConstraints periodConstraints,
        StrategyCandidateSimulation simulation,
        AdjustStrategySimulationCommand command
) {
}
