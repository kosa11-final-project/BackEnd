package com.stockit.backend.feature.strategy.calculation.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyCalculationPolicyVO {
    private Long inventoryPolicyId;
    private Long warehouseId;
    private Long stockSalesPointId;
    private Long allocatedSalesPointId;
    private BigDecimal safetyStockQty;
    private BigDecimal targetStockQty;
    private BigDecimal dailyUnitHoldingCost;
    private BigDecimal unitDisposalCost;
}
