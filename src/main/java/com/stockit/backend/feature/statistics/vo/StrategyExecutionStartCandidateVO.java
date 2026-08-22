package com.stockit.backend.feature.statistics.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyExecutionStartCandidateVO {
    private Long finalSelectionId;
    private Long ownerUserId;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private BigDecimal goalTargetValue;
    private Integer snapshotCount;
    private BigDecimal startRiskStockQty;
    private BigDecimal startExpectedDisposalQty;
    private BigDecimal startUnitCost;
}
