package com.stockit.backend.feature.strategy.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdjustStrategySimulationCommand(
        BigDecimal actionQuantity,
        BigDecimal discountRate,
        LocalDate startDate,
        LocalDate endDate
) {
}
