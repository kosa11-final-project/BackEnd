package com.stockit.backend.feature.statistics.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyStatisticsResultVO {

    private Long finalSelectionId;
    private Long strategyCaseId;
    private Long strategyOptionId;
    private LocalDate executionEndDate;
    private BigDecimal achievementRate;
    private BigDecimal startRiskStockQty;
    private BigDecimal endRiskStockQty;
    private BigDecimal startExpectedDisposalQty;
    private BigDecimal endExpectedDisposalQty;
    private BigDecimal estimatedLossSavingsAmount;
}
