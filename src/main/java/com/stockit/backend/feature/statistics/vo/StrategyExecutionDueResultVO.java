package com.stockit.backend.feature.statistics.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyExecutionDueResultVO {
    private Long finalSelectionId;
    private Long ownerUserId;
    private Long finalizedSyncRunId;
    private BigDecimal goalTargetValue;
    private BigDecimal goalActualValue;
    private BigDecimal startRiskStockQty;
    private BigDecimal endRiskStockQty;
    private BigDecimal startExpectedDisposalQty;
    private BigDecimal endExpectedDisposalQty;
    private BigDecimal startUnitCost;
}
