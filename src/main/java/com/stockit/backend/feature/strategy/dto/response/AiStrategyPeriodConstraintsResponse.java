package com.stockit.backend.feature.strategy.dto.response;

import java.time.LocalDate;

import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy.PeriodConstraints;

/** 현재 시점과 배정 LOT를 기준으로 한 전략 기간 조정 범위. */
public record AiStrategyPeriodConstraintsResponse(
        LocalDate minimumStartDate,
        LocalDate latestSelectableEndDate,
        int maximumPeriodDays,
        boolean requiresPeriodAdjustment
) {
    public static AiStrategyPeriodConstraintsResponse from(PeriodConstraints constraints) {
        return new AiStrategyPeriodConstraintsResponse(
                constraints.minimumStartDate(),
                constraints.latestSelectableEndDate(),
                constraints.maximumPeriodDays(),
                constraints.requiresPeriodAdjustment()
        );
    }
}
