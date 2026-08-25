package com.stockit.backend.feature.strategy.calculation.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyCalculationInventoryVO {
    private Long inventoryBalanceId;
    private Long skuId;
    private Long warehouseId;
    private Long stockSalesPointId;
    private Long allocatedSalesPointId;
    private Long lotId;
    private BigDecimal onHandQty;
    private BigDecimal reservedQty;
    private LocalDate manufacturedDate;
    private LocalDate receivedDate;
    private LocalDate expiryDate;
    private LocalDate saleStopDate;
    private String lotStatus;

    public Long effectiveSalesPointId() {
        return stockSalesPointId != null ? stockSalesPointId : allocatedSalesPointId;
    }

    public boolean isPublicUnassigned() {
        return stockSalesPointId == null && allocatedSalesPointId == null;
    }
}
