package com.stockit.backend.feature.strategy.calculation.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StrategyCalculationWarehouseRouteVO {
    private Long salesPointWarehouseId;
    private Long salesPointId;
    private Long warehouseId;
    private Integer priorityNo;
    private BigDecimal baseDeliveryCost;
}
