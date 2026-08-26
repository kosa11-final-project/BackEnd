package com.stockit.backend.feature.strategy.calculation.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyCalculationTransferCostPolicyVO {
    private Long transferCostPolicyId;
    private String policyCode;
    private BigDecimal costPerKgKm;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
