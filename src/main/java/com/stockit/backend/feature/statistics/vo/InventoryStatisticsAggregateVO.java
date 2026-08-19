package com.stockit.backend.feature.statistics.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryStatisticsAggregateVO {

    private String scopeType;
    private Long warehouseId;
    private Long salesPointId;
    private String scopeCode;
    private String scopeName;
    private String regionCode;
    private long totalSkuCount;
    private BigDecimal totalStockQty;
    private BigDecimal availableStockQty;
    private long criticalSkuCount;
    private long warningSkuCount;
    private long normalSkuCount;
    private long goodSkuCount;
    private long unassessedDistributionSkuCount;
    private BigDecimal criticalStockQty;
    private BigDecimal warningStockQty;
    private BigDecimal normalStockQty;
    private BigDecimal goodStockQty;
    private BigDecimal unassessedDistributionStockQty;
    private long shortageSkuCount;
    private BigDecimal expectedDisposalQty30d;
    private BigDecimal totalInventoryCostAmount;
    private BigDecimal criticalInventoryCostAmount;
    private BigDecimal expectedDisposalLossAmount30d;
    private long missingCostSkuCount;
    private BigDecimal missingCostStockQty;
    private long unassessedSkuCount;
    private BigDecimal unassessedStockQty;
    private long missingForecastSkuCount;
    private BigDecimal missingForecastStockQty;
}
