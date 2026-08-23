package com.stockit.backend.feature.strategy.calculation.candidate.domain;

import java.time.LocalDate;

public record StrategyPeriodCandidate(LocalDate startDate, LocalDate endDate) {

    public StrategyPeriodCandidate {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("strategy period candidate is invalid");
        }
    }
}
